import kotlinx.atomicfu.*

class AtomicArray<E : Any>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int): E {
        a[index].loop {
            when (it) {
                is Descriptor -> it.complete()
                else -> return it as E
            }
        }
    }

    fun set(index: Int, value: E) {
        a[index].loop {
            when (it) {
                is Descriptor -> it.complete()
                else -> a[index].value = value
            }
        }
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        while (true) {
            if (a[index].compareAndSet(expected, update))
                return true

            val value = a[index].value

            // If CAS failed but value is not descriptor, it is possible that value changed from descriptor to element
            // between CAS and reading value. In that case we should compare expected with value and return with false
            // if they are not equal.
            if (value is Descriptor) {
                value.complete()
            } else if (value != expected)
                return false
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
        if (!a[descriptor.indexA].compareAndSet(descriptor.expectedA, descriptor)) {
            val value = a[descriptor.indexA].value

            if (value is Descriptor && value != descriptor)
                value.complete()

            if (!a[descriptor.indexA].compareAndSet(descriptor.expectedA, descriptor))
                return false
        }

        descriptor.complete()
        return descriptor.isSuccessful()
    }

    private fun doubleCompareSingleSwap(
        indexA: Int, expectedA: Any, updateA: Any,
        indexB: Int, expectedB: Any
    ): Boolean {
        var descriptor = DcssDescriptor(indexA, expectedA, updateA, indexB, expectedB)

        if (!a[indexA].compareAndSet(expectedA, descriptor)) {
            val value = a[indexA].value

            if (value !is Descriptor) {
                if (value != expectedA)
                    return false

                if (!a[indexA].compareAndSet(expectedA, descriptor))
                    return false
            } else if (value is AtomicArray<*>.CasnDescriptor) {
                if (value != descriptor.updateA)
                    value.complete()

                if (!a[indexA].compareAndSet(expectedA, descriptor))
                    return false
            } else if (value is AtomicArray<*>.DcssDescriptor) {
                if (descriptor.updateA != value.updateA)
                    return false

                descriptor = value as AtomicArray<E>.DcssDescriptor
            } else {
                return false
            }
        }

        descriptor.complete()
        return descriptor.isSuccessful()
    }

    private interface Descriptor {
        fun complete()
        fun isSuccessful(): Boolean
    }

    private inner class DcssDescriptor(
        val indexA: Int,
        val expectedA: Any,
        val updateA: Any,
        val indexB: Int,
        val expectedB: Any,
    ) : Descriptor {
        override fun complete() {
            // Defining the outcome for DCSS operation.
            val b = a[indexB].value

            val casnDescriptor = updateA as AtomicArray<*>.CasnDescriptor
            val casnOutcome = casnDescriptor.outcome.value

            if (b != expectedB) {
                casnDescriptor.outcome.compareAndSet(CasnOutcome.UNDEFINED, CasnOutcome.FAILURE)
            } else if (casnOutcome == CasnOutcome.UNDEFINED) {
                casnDescriptor.outcome.compareAndSet(CasnOutcome.UNDEFINED, CasnOutcome.SUCCESS)
            }

            if (casnDescriptor.outcome.value == CasnOutcome.SUCCESS) {
                a[indexA].compareAndSet(this, updateA)
            } else {
                a[indexA].compareAndSet(this, expectedA)
            }
        }

        override fun isSuccessful(): Boolean {
            val casnDescriptor = updateA as AtomicArray<*>.CasnDescriptor
            val casnOutcome = casnDescriptor.outcome.value

            return casnOutcome == CasnOutcome.SUCCESS
        }
    }

    private inner class CasnDescriptor(
        val indexA: Int,
        val indexB: Int,
        val expectedA: E,
        val expectedB: E,
        val updateA: E,
        val updateB: E) : Descriptor
    {
        val outcome: AtomicRef<CasnOutcome> = atomic(CasnOutcome.UNDEFINED)

        override fun complete() {
            // Point of linearization?
            val b = a[indexB].value

            if (b is AtomicArray<*>.DcssDescriptor)
                b.complete()

            if (!doubleCompareSingleSwap(indexA = indexB, expectedA = expectedB, updateA = this, indexB = indexA, expectedB = this) && a[indexB].value != this) {
                outcome.compareAndSet(CasnOutcome.UNDEFINED, CasnOutcome.FAILURE)
            } else {
                outcome.compareAndSet(CasnOutcome.UNDEFINED, CasnOutcome.SUCCESS)
            }

            if (outcome.value == CasnOutcome.FAILURE) {
                setIfDescriptor(indexA, expectedA)
                //setIfDescriptor(indexB, expectedB)
                return
            }

            if (outcome.value == CasnOutcome.SUCCESS) {
                setIfDescriptor(indexA, updateA)
                setIfDescriptor(indexB, updateB)
                return
            }
        }

        override fun isSuccessful(): Boolean {
            return outcome.value == CasnOutcome.SUCCESS
        }

        fun setIfDescriptor(index: Int, value: E): Boolean {
            return a[index].compareAndSet(this, value)
        }
    }

    private enum class CasnOutcome {
        UNDEFINED,
        SUCCESS,
        FAILURE,
    }
}