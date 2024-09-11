import Decision.*
import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Ref<E>>(size)

    class Ref<T>(initial: T) {
        private val v = atomic<Any?>(initial)

        var value: T
            get() = v.loop {
                when (it) {
                    is Descriptor -> {
                        it.complete()
                    }

                    else -> return it as T
                }
            }
            set(value) = v.loop { cur ->
                when (cur) {
                    is Descriptor -> cur.complete()
                    else -> if (cas(cur as T, value)) return
                }
            }

        fun cas(expected: Any?, update: Any?) = v.compareAndSet(expected, update)
    }

    init {
        for (i in 0 until size) a[i].value = Ref(initialValue)
    }

    fun get(index: Int) =
        a[index].value!!.value

    fun cas(index: Int, expected: E, update: E) =
        a[index].value!!.cas(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        val indexes = listOf(index1, index2)
            .zip(listOf(expected1, expected2))
            .zip(listOf(update1, update2))
            { (index, expected), update -> CasData(index, expected, update) }
            .sortedBy { it.index }

        val a = this.a[indexes[0].index].value!!
        val expectedA = indexes[0].expected
        val updateA = indexes[0].update
        val b = this.a[indexes[1].index].value!!
        val expectedB = indexes[1].expected
        val updateB = indexes[1].update
        val casnDescriptor = CasnDescriptor(a, expectedA, updateA, b, expectedB, updateB)

        if (!a.cas(expectedA, casnDescriptor)) {
            casnDescriptor.outcome.cas(UNDECIDED, FAILED)
            return false
        }

        casnDescriptor.complete()

        return casnDescriptor.outcome.value == SUCCESS


    }
}

data class CasData<E>(
    val index: Int,
    val expected: E,
    val update: E
)

interface Descriptor {
    fun complete()
}


enum class Decision {
    UNDECIDED,
    SUCCESS,
    FAILED
}

class CasnDescriptor<E>(
    private val a: AtomicArrayNoAba.Ref<E>, private val expectedA: E, private val updateA: E,
    private val b: AtomicArrayNoAba.Ref<E>, private val expectedB: E, private val updateB: E
) : Descriptor {
    val outcome = AtomicArrayNoAba.Ref(UNDECIDED)

    override fun complete() {
        if (a.value == this) {
            if (b.cas(expectedB, this)) {
                outcome.value = SUCCESS
                a.cas(this, updateA)
                b.cas(this, updateB)
            } else {
                outcome.cas(UNDECIDED, FAILED)
                a.cas(this, expectedA)
            }
        }
    }

}

fun <A, B> dcss(
    a: AtomicArrayNoAba.Ref<A>, expected1: A, update1: Any,
    b: AtomicArrayNoAba.Ref<B>, expected2: B
): Boolean {
    val dcssDescriptor = DCSSDescriptor(a, expected1, update1, b, expected2)
    if (!a.cas(expected1, dcssDescriptor)) {
        dcssDescriptor.outcome.cas(UNDECIDED, FAILED)
        return false
    }

    dcssDescriptor.complete()

    return dcssDescriptor.outcome.value == SUCCESS
}

class DCSSDescriptor<A, B>(
    private val a: AtomicArrayNoAba.Ref<A>, private val expectedA: A, private val updateA: Any,
    private val b: AtomicArrayNoAba.Ref<B>, private val expectedB: B
) : Descriptor {
    val outcome = AtomicArrayNoAba.Ref(UNDECIDED)

    override fun complete() {
        if (b.value == expectedB) {
            outcome.cas(UNDECIDED, SUCCESS)
            a.cas(this, updateA)
        } else {
            outcome.cas(UNDECIDED, FAILED)
            a.cas(this, expectedA)
        }
    }

}