import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any>(size)


    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        a[index].loop {
            if (it is AtomicArrayNoAba<*>.DescriptorCAS2) it.complete()
            else return it as E
        }
    }

    fun set(index: Int, value: E) {
        a[index].loop {
            if (it is AtomicArrayNoAba<*>.DescriptorCAS2) it.complete()
            else if (a[index].compareAndSet(it, value)) return
        }
    }

    fun cas(index: Int, expected: E, update: E) =
        a[index].compareAndSet(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        if (index1 == index2) {
            if (expected1 != expected2) return false
            return cas(index1, expected1, update2)
        }


        return if (index1 > index2) {
            cas2Runner(index2, expected2, update2, index1, expected1, update1)
        } else {
            cas2Runner(index1, expected1, update1, index2, expected2, update2)
        }
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        /*
        if (a[index1].value != expected1 || a[index2].value != expected2) return false
        a[index1].value = update1
        a[index2].value = update2
        return true
        */
    }

    private fun cas2Runner(index1: Int, expected1: E, update1: E, index2: Int, expected2: E, update2: E): Boolean {
        val descriptor = DescriptorCAS2(index1, expected1, update1, index2, expected2, update2)

        if (casDescriptor(index1, expected1, descriptor)) {
            descriptor.complete()
            return descriptor.consensus.value == State.COMPLETED
        }

        return false
    }

    private fun casDescriptor(index: Int, expected: E, update: Any): Boolean {
        while (true) {
            val value = get(index)

            if (value != expected) return false
            if (a[index].compareAndSet(expected, update)) return true
        }
    }

    private inner class DescriptorCAS2(
        val index1: Int, val expected1: E, val update1: E,
        val index2: Int, val expected2: E, val update2: E
    ) {
        val consensus = atomic(State.UNKNOWN)
        fun complete() {
//            val one = a[index1]
//            val two = a[index2]
            if (a[index2].value == this) {
                consensus.compareAndSet(State.UNKNOWN, State.COMPLETED)
                a[index1].compareAndSet(this, update1)
                a[index2].compareAndSet(this, update2)
            } else {
                consensus.compareAndSet(State.UNKNOWN, State.FAILED)
                a[index1].compareAndSet(this, expected1)
                a[index2].compareAndSet(this, expected2)
            }
        }
    }

    private enum class State {
        UNKNOWN, COMPLETED, FAILED
    }

}
