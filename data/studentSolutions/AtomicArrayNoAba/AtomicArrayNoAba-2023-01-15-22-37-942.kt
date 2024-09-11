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
                        it.help()
                    }
                    else -> return it as T
                }
            }
            set(value) = v.loop { cur ->
                when (cur) {
                    is Descriptor -> cur.help()
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

    fun cas(index: Int, expected: Int, update: Int) =
        a[index].value!!.cas(expected, update)

    fun cas2(
        index1: Int, expected1: Int, update1: Int,
        index2: Int, expected2: Int, update2: Int
    ): Boolean {
        if (index1 == index2 && update1 != update2) return false
        if (index1 == index2)
            return cas(index1, expected1, expected1 + 2)

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

        a.cas(expectedA, casnDescriptor)
        if (!a.cas(casnDescriptor, casnDescriptor)) {
            casnDescriptor.outcome.compareAndSet(UNDECIDED, FAILED) // 1 -> 7
            return false
        }

        casnDescriptor.complete() // 1 -> 2

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
    fun help()
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
        when (outcome.value) {
            FAILED -> a.cas(this, expectedA) // 8 -> 9
            SUCCESS -> b.cas(this, updateB) // 5 -> 6
            else -> {
                if (outcome.compareAndSet(UNDECIDED, UNDECIDED)) {
                    b.cas(expectedB, this) // 2 -> 3
                }
                if (b.cas(this, this)) {
                    outcome.compareAndSet(UNDECIDED, SUCCESS) // 3 -> 4
                    a.cas(this, updateA) // 4 -> 5
                } else {
                    outcome.compareAndSet(UNDECIDED, FAILED) // 2 -> 8
                }
            }
        }
    }

    override fun help() {
        when (outcome.value) {
            FAILED -> a.cas(this, expectedA) // 8 -> 9
            SUCCESS -> b.cas(this, updateB) // 5 -> 6
            else -> {
                if (outcome.compareAndSet(UNDECIDED, UNDECIDED)) {
                    b.cas(expectedB, this) // 2 -> 3
                }
                if (b.cas(this, this)) {
                    outcome.compareAndSet(UNDECIDED, SUCCESS) // 3 -> 4
                    a.cas(this, updateA) // 4 -> 5
                } else {
                    outcome.compareAndSet(UNDECIDED, FAILED) // 2 -> 8
                }
            }
        }
    }

}