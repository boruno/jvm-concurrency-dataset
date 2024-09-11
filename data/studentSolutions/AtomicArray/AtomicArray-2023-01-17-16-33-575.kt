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
        a[index].loop {
            when (it) {
                is Descriptor -> it.complete()
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
        if (!a[index1].compareAndSet(expected1, descriptor))
            return false

        descriptor.complete()
        return descriptor.outcome.value == CasnOutcome.SUCCESS
    }

    private fun doubleCompareSingleSwap(
        indexA: Int, expectedA: Any, updateA: Any,
        indexB: Int, expectedB: Any
    ): Boolean {
        val descriptor = DcssDescriptor(indexA, expectedA, updateA, indexB, expectedB)

        if (!a[indexA].compareAndSet(expectedA, descriptor))
            return false

        descriptor.complete()
        return descriptor.outcome.value == CasnOutcome.SUCCESS
    }

    private interface Descriptor {
        fun complete()
    }

    private inner class DcssDescriptor(
        val indexA: Int,
        val expectedA: Any,
        val updateA: Any,
        val indexB: Int,
        val expectedB: Any,
    ) : Descriptor {
        val outcome: AtomicRef<CasnOutcome> = atomic(CasnOutcome.UNDEFINED)
        override fun complete() {
            // Defining the outcome for DCSS operation.
            val b = a[indexB].value

            if (b != expectedB) {
                outcome.compareAndSet(CasnOutcome.UNDEFINED, CasnOutcome.FAILURE)
            } else {
                outcome.compareAndSet(CasnOutcome.UNDEFINED, CasnOutcome.SUCCESS)
            }

            if (outcome.value == CasnOutcome.SUCCESS) {
                a[indexA].compareAndSet(this, updateA)
            } else {
                a[indexA].compareAndSet(this, expectedA)
            }
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
            var b = a[indexB].value

            println("A: $indexA (${a[indexA].value}); B: $indexB (${a[indexB].value}); this: $this")

            if (b is AtomicArray<*>.DcssDescriptor) {
                var s = "\nA: $indexA (${a[indexA].value}); B: $indexB (${a[indexB].value}); DCSS: A: ${b.indexA}, B: ${b.indexB}, eA: ${b.expectedA}, eB: ${b.expectedB}, uA: ${b.updateA}, o: ${b.outcome.value}"
                b.complete()
                s += '\n'
                s += "A: $indexA (${a[indexA].value}); B: $indexB (${a[indexB].value}); DCSS: A: ${b.indexA}, B: ${b.indexB}, eA: ${b.expectedA}, eB: ${b.expectedB}, uA: ${b.updateA}, o: ${b.outcome.value}"
                println(s)
            }

            b = a[indexB].value

            if (b != this) {
                error("A: $indexA (${a[indexA].value}); B: $indexB (${a[indexB].value}); this: $this")
            }

            if (b != this && !doubleCompareSingleSwap(indexA = indexB, expectedA = expectedB, updateA = this, indexB = indexA, expectedB = this)) {
                outcome.compareAndSet(CasnOutcome.UNDEFINED, CasnOutcome.FAILURE)
            } else {
                outcome.compareAndSet(CasnOutcome.UNDEFINED, CasnOutcome.SUCCESS)
            }

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