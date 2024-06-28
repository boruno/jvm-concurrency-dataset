import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any>(size)

    init {
        for (i in 0..size-1) a[i].value = initialValue
    }

    fun get(index: Int): E {
        while (true) {
            val element = a[index].value
            if (element is AtomicArrayNoAba<*>.CasDescriptor) {
                element.complete()
            } else {
                return element as E
            }
        }
    }

    fun cas(index: Int, expected: E, update: Any): Boolean {
        while (true) {
            val value = get(index)
            if (value is AtomicArrayNoAba<*>.CasDescriptor) {
                value.complete()
            } else if (value != expected) return false
            if (a[index].compareAndSet(expected, update)) return true
        }
    }

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        if (index1 == index2) {
            return if (expected1 == expected2) cas(index1, expected1, update1 as Any) else false
        }
        if (index1 < index2) return hierarchicalCas2(index1, expected1, update1, index2, expected2, update2)

        return hierarchicalCas2(index2, expected2, update2, index1, expected1, update1)
    }

    private fun hierarchicalCas2(index1: Int, expected1: E, update1: E, index2: Int, expected2: E, update2: E): Boolean {
        val descriptor = CasDescriptor(index1, expected1, update1, index2, expected2, update2)

        if (cas(index1, expected1, descriptor)) {
            descriptor.complete()
            return descriptor.state.value == State.COMPLETED
        }

        return false
    }

    private inner class CasDescriptor(
        val index1: Int, val expected1: E, val update1: E,
        val index2: Int, val expected2: E, val update2: E
    ) {
        val state = atomic(State.UNDECIDED)

        fun complete() {
            cas(index2, expected2, update1 as Any)
            if (a[index2].value === this) {
                state.compareAndSet(State.UNDECIDED, State.COMPLETED)
                a[index1].compareAndSet(this, update1)
                a[index2].compareAndSet(this, update2)
            } else {
                state.compareAndSet(State.UNDECIDED, State.FAILED)
                a[index1].compareAndSet(this, expected1)
                a[index2].compareAndSet(this, expected2)
            }
        }
    }

    private enum class State {
        UNDECIDED, COMPLETED, FAILED
    }
}