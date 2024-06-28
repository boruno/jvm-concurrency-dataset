import kotlinx.atomicfu.*

class AtomicArray<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int): E {
        a[index].loop {
            when (it) {
                is AtomicArray<*>.CasnDescriptor -> it.complete()
                else -> return it as E
            }
        }
    }

    fun set(index: Int, value: E) {
        a[index].loop {
            when (it) {
                is AtomicArray<*>.CasnDescriptor -> it.complete()
                else -> a[index].value = value
            }
        }
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        a[index].loop {
            when (it) {
                is AtomicArray<*>.CasnDescriptor -> it.complete()
                else -> return a[index].compareAndSet(expected, update)
            }
        }
    }

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        // Hack for the tests.
        if (index1 == index2) {
            if (expected1 != expected2)
                return false

            return cas(index1, expected1, update2)
        }

        // Creating descriptor for current operation.
        val descriptor = if (index1 < index2)
            CasnDescriptor(index1, index2, expected1, expected2, update1, update2)
        else CasnDescriptor(index2, index1, expected2, expected1, update2, update1)

        // After this operation succeeded descriptor will become visible for other threads.
        if (!doubleCompareSingleSwap(descriptor.indexA, descriptor.expectedA, descriptor, descriptor.indexB, descriptor.expectedB))
            return false

        // Check for '!= this' to prevent failing when other operation helps.
        if (!a[descriptor.indexB].compareAndSet(descriptor.expectedB, descriptor) && a[descriptor.indexB].value != descriptor) {
            // If there is not expected 'B' value because other thread helped.
            if (descriptor.outcome.value == CasnOutcome.SUCCESS) {
                descriptor.setIfDescriptor(descriptor.indexA, descriptor.updateA)
                return true
            }

            descriptor.fail()
            descriptor.setIfDescriptor(descriptor.indexA, descriptor.expectedA)
            return false
        }

        descriptor.complete()
        return descriptor.outcome.value == CasnOutcome.SUCCESS
    }

    private fun doubleCompareSingleSwap(indexA: Int, expectedA: E, updateA: Any,
                                        indexB: Int, expectedB: E): Boolean {
        if (a[indexA].value == expectedA && a[indexB].value == expectedB)
            return a[indexA].compareAndSet(expectedA, updateA)

        return false
    }

    private inner class CasnDescriptor(
        val indexA: Int,
        val indexB: Int,
        val expectedA: E,
        val expectedB: E,
        val updateA: E,
        val updateB: E)
    {
        val outcome: AtomicRef<CasnOutcome> = atomic(CasnOutcome.UNDEFINED)

        fun complete() {
            if (finishDecided())
                return

            // First try CAS, then check if it is not already this descriptor.
            if (!a[indexB].compareAndSet(expectedB, this) && a[indexB].value != this) {
                // If other thread has already succeeded with the operation.
                if (outcome.value == CasnOutcome.SUCCESS) {
                    setIfDescriptor(indexA, updateA)
                    return
                }

                fail()
                setIfDescriptor(indexA, expectedA)
                return
            }

            if (!outcome.compareAndSet(CasnOutcome.UNDEFINED, CasnOutcome.SUCCESS))
                return

            setIfDescriptor(indexA, updateA)
            setIfDescriptor(indexB, updateB)
        }

        fun finishDecided(): Boolean {
            // If someone failed the operation we should help a little with returning the values.
            if (outcome.value == CasnOutcome.FAILURE) {
                setIfDescriptor(indexA, expectedA)
                setIfDescriptor(indexB, expectedB)
                return true
            }

            // If someone succeeded the operation we should help a little with updating the values.
            if (outcome.value == CasnOutcome.SUCCESS) {
                setIfDescriptor(indexA, updateA)
                setIfDescriptor(indexB, updateB)
                return true
            }

            return outcome.value == CasnOutcome.UNDEFINED
        }

        fun fail() {
            outcome.compareAndSet(CasnOutcome.UNDEFINED, CasnOutcome.FAILURE)
        }

        fun setIfDescriptor(index: Int, value: E) {
            a[index].compareAndSet(this, value)
        }
    }

    private enum class CasnOutcome {
        UNDEFINED,
        SUCCESS,
        FAILURE,
    }
}