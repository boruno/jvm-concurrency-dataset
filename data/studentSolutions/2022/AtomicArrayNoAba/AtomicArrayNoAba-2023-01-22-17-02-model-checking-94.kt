import kotlinx.atomicfu.*

@Suppress("UNCHECKED_CAST")
class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int): E {
        a[index].loop { value ->
                if (value is AtomicArrayNoAba<*>.CasDescriptor) {
                    value.complete()
                }
                return value as E
        }
    }

    fun cas(index: Int, expected: Int, update: Int) = check(index, expected, update as Any)

    fun cas2(index1: Int, expected1: Int, update1: Int, index2: Int, expected2: Int, update2: Int): Boolean {
        if (index1 == index2) {
            return cas(index1, expected1, expected1 + 2)
        }
        if (index1 > index2) return cas2ex(index2, expected2, update2, index1, expected1, update1)
        return cas2ex(index1, expected1, update1, index2, expected2, update2)
    }

    private fun cas2ex(index1: Int, expected1: Int, update1: Int, index2: Int, expected2: Int, update2: Int): Boolean {
        val descriptor = CasDescriptor(index1, expected1, update1, index2, expected2, update2)

        if (check(index1, expected1, descriptor)) {
            descriptor.complete()
            return descriptor.result() == Result.SUCCESS
        }

        return false
    }

    private fun check(index: Int, expected: Int, update: Any): Boolean {
        while (true) {
            if (get(index) != expected) return false
            return a[index].compareAndSet(expected, update)
        }
    }


    private enum class Result {
        UNDECIDED, SUCCESS, FAILED
    }

    private abstract class Descriptor {
        abstract fun complete()
    }

    private inner class CasDescriptor(
        val index1: Int, val expected1: Int, val update1: Int,
        val index2: Int, val expected2: Int, val update2: Int
    ) : Descriptor() {
        val state = atomic(Result.UNDECIDED)
        fun result(): Result {
            return state.value
        }

        override fun complete() {
            a[index2].compareAndSet(expected2, this)
            if (a[index2].value === this) {
                state.compareAndSet(Result.UNDECIDED, Result.SUCCESS)
                a[index1].compareAndSet(this, update1)
                a[index2].compareAndSet(this, update2)
            } else {
                state.compareAndSet(Result.UNDECIDED, Result.FAILED)
                a[index1].compareAndSet(this, expected1)
                a[index2].compareAndSet(this, expected2)
            }
        }

    }

}