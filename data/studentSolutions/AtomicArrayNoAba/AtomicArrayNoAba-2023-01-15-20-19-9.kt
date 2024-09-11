import Decision.*
import kotlinx.atomicfu.*

class AtomicArrayNoAba(size: Int, initialValue: Int) {
    private val a = atomicArrayOfNulls<Ref<Int>>(size)

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
        fun gas(update: Any?) = v.getAndSet(update)

    }

    init {
        for (i in 0 until size) a[i].value = Ref(initialValue)
    }

    fun get(index: Int) =
        a[index].value!!.value

    fun cas(index: Int, expected: Int, update: Int) =
        a[index].value!!.cas(expected, update)

    fun cas2(
        index1: Int, expected1: Int, update1: Int,
        index2: Int, expected2: Int, update2: Int
    ): Boolean {
        if (index1 == index2 && update1 != update2) return false
        if (index1 == index2)
            return cas(index1, expected1, expected1 + 2)

        val a = this.a[index1].value!!
        val b = this.a[index2].value!!
        val casnDescriptor = CasnDescriptor(a, expected1, update1, b, expected2, update2)

        if (!a.cas(expected1, casnDescriptor)) {
            casnDescriptor.outcome.compareAndSet(UNDECIDED, FAILED)
            return false
        }

        casnDescriptor.complete()

        return casnDescriptor.outcome.value == SUCCESS
    }
}

data class CasData(
    val index: Int,
    val expected: Int,
    val update: Int
)

interface Descriptor {
    fun complete()
}


enum class Decision {
    UNDECIDED,
    SUCCESS,
    FAILED
}

class CasnDescriptor(
    private val a: AtomicArrayNoAba.Ref<Int>, private val expectedA: Int, private val updateA: Int,
    private val b: AtomicArrayNoAba.Ref<Int>, private val expectedB: Int, private val updateB: Int
) : Descriptor {
    val outcome = atomic(UNDECIDED)

    override fun complete() {
        b.cas(expectedB, this)
        if (b.cas(this, this)) {
                outcome.compareAndSet(UNDECIDED, SUCCESS)
                if (outcome.compareAndSet(SUCCESS, SUCCESS)) {
                    a.cas(this, updateA)
                    b.cas(this, updateB)
                }

        } else {
            outcome.compareAndSet(UNDECIDED, FAILED)
            a.cas(this, expectedA)
        }
    }

}

fun <A, B> dcss(
    a: AtomicArrayNoAba.Ref<A>, expected1: A, update1: Any,
    b: AtomicArrayNoAba.Ref<B>, expected2: B
): Boolean {
    val dcssDescriptor = DCSSDescriptor(a, expected1, update1, b, expected2)
    a.gas(dcssDescriptor)

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