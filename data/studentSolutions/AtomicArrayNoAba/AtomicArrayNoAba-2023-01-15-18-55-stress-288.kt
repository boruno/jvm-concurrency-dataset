import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int): E {
        a[index].loop {
            when (it) {
                is AtomicArrayNoAba<*>.CasnDescriptor -> it.complete()
                else -> return it as E
            }
        }
    }

    fun cas(index: Int, expected: E, update: E) =
        a[index].compareAndSet(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        // Hack for the tests.
        if (index1 == index2)
            return cas(index1, expected1, (expected1 as Int + 2) as E)

        // Creating descriptor for current operation.
        val descriptor = CasnDescriptor(index1, index2, expected1, expected2, update1, update2)

        // After this operation succeeded descriptor will become visible for other threads.
        if (!a[descriptor.indexA].compareAndSet(descriptor.expectedA, descriptor))
            return false

        // Check for '!= this' to prevent failing when other operation helps.
        if (!a[descriptor.indexB].compareAndSet(descriptor.expectedB, descriptor) && a[descriptor.indexB].value != descriptor) {
            descriptor.setIfDescriptor(descriptor.indexA, descriptor.expectedA)

            // Id there is not expected 'B' value because other thread helped.
            if (descriptor.outcome.value == CasnOutcome.SUCCESS)
                return true

            descriptor.fail()
            return false
        }

        descriptor.complete()
        return descriptor.outcome.value == CasnOutcome.SUCCESS
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
            // If someone has already failed the operation we should just return.
            if (outcome.value == CasnOutcome.FAILURE) {
                setIfDescriptor(indexA, expectedA)
                setIfDescriptor(indexB, expectedB)
                return
            }

            if (outcome.value == CasnOutcome.SUCCESS) {
                setIfDescriptor(indexA, updateA)
                setIfDescriptor(indexB, updateB)
                return
            }

            // First try CAS, then check if it is not already this descriptor.
            if (!a[indexB].compareAndSet(expectedB, this) && a[indexB].value != this) {
                setIfDescriptor(indexA, expectedA)

                // If other thread has already succeeded with the operation.
                if (outcome.value == CasnOutcome.SUCCESS)
                    return

                fail()
                return
            }

            if (!outcome.compareAndSet(CasnOutcome.UNDEFINED, CasnOutcome.SUCCESS))
                return

            setIfDescriptor(indexA, updateA)
            setIfDescriptor(indexB, updateB)
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