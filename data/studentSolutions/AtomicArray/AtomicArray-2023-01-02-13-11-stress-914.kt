import kotlinx.atomicfu.*

@Suppress("UNCHECKED_CAST")
class AtomicArray<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int): E? {
        a[index].loop { value ->
            when (value) {
                is Descriptor -> value.complete()
                else -> return value as E?
            }
        }
    }

    fun set(index: Int, value: E) {
        a[index].loop { curValue ->
            when (curValue) {
                is Descriptor -> curValue.complete()
                else -> if (a[index].compareAndSet(curValue, value)) return
            }
        }
    }

    fun cas(index: Int, expected: E, update: E) = abstractCas(index, expected, update as Any)

    fun cas2(index1: Int, expected1: E, update1: E, index2: Int, expected2: E, update2: E): Boolean {
        if (index1 == index2) {
            if (expected1 == expected2) return cas(index2, expected2, update2)
            return false
        }
        if (index1 < index2) return hierarchicalCas2(index1, expected1, update1, index2, expected2, update2)

        return hierarchicalCas2(index2, expected2, update2, index1, expected1, update1)
    }

    private fun hierarchicalCas2(index1: Int, expected1: E, update1: E, index2: Int, expected2: E, update2: E): Boolean {
        val descriptor = CasDescriptor(index1, expected1, update1, index2, expected2, update2)

        if (abstractCas(index1, expected1, descriptor)) {
            descriptor.complete()
            return descriptor.state.value == State.COMPLETED
        }

        return false
    }

    private fun abstractCas(index: Int, expected: E, update: Any): Boolean {
        while (true) {
            val value = get(index)

            if (value != expected) return false
            if (a[index].compareAndSet(expected, update)) return true
        }
    }

    private abstract class Descriptor {
        abstract fun complete()
    }

    private inner class CasDescriptor(
        val index1: Int, val expected1: E, val update1: E,
        val index2: Int, val expected2: E, val update2: E
    ) : Descriptor() {
        val state = atomic(State.UNDECIDED)

        override fun complete() {
            dcss(index2, expected2, this)

            if (a[index2].value === this) {
                state.compareAndSet(State.UNDECIDED, State.COMPLETED)
                a[index1].compareAndSet(this, update1)
                a[index2].compareAndSet(this, update2)
            } else {
                state.compareAndSet(State.UNDECIDED, State.FAILED)
                a[index1].compareAndSet(this, expected1)
//                a[index2].compareAndSet(this, expected2)
            }
        }

        private fun dcss(index: Int, expected: E, update: CasDescriptor) {
            val descriptor = DcssDescriptor(index, expected, update)

            while (true) {
                val value = a[index].value

                if (value is Descriptor) {
                    if (value === this) return

                    value.complete()
                    continue
                }

                if (value != expected) return

                if (a[index].compareAndSet(expected, descriptor)) {
                    descriptor.complete()
                    return
                }
            }
        }
    }

    private inner class DcssDescriptor(val index: Int, val expected: E, val update: CasDescriptor) : Descriptor() {
        override fun complete() {
            val value: Any? = if (update.state.value == State.UNDECIDED) update else expected

            a[index].compareAndSet(this, value)
        }
    }

    private enum class State {
        UNDECIDED, COMPLETED, FAILED
    }
}
