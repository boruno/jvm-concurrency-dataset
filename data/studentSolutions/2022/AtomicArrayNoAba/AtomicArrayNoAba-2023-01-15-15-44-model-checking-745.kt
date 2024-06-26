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
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        val descriptor = CasnDescriptor(index1, index2, expected1, expected2, update1, update2)

        if (a[descriptor.indexA].value != descriptor.expectedA)
            return false

        if (!a[descriptor.indexA].compareAndSet(descriptor.expectedA, descriptor))
            return false

        if (a[descriptor.indexB].value != descriptor.expectedB) {
            descriptor.outcome.compareAndSet(CasnOutcome.UNDEFINED, CasnOutcome.FAILURE)
            a[descriptor.indexA].value = descriptor.expectedA
            return false
        }

        if (!a[descriptor.indexB].compareAndSet(descriptor.expectedB, descriptor)) {
            descriptor.outcome.compareAndSet(CasnOutcome.UNDEFINED, CasnOutcome.FAILURE)
            a[descriptor.indexA].value = descriptor.expectedA
            return false
        }

        descriptor.complete()
        return true
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
            if (a[indexA].value != this) {
                return
            }

            if (a[indexB].value != this && !a[indexB].compareAndSet(expectedB, this)) {
                outcome.compareAndSet(CasnOutcome.UNDEFINED, CasnOutcome.FAILURE)
                a[indexA].value = expectedA
                return
            }

            if (!outcome.compareAndSet(CasnOutcome.UNDEFINED, CasnOutcome.SUCCESS))
                return

            a[indexA].value = updateA
            a[indexB].value = updateB
        }
    }

    private enum class CasnOutcome {
        UNDEFINED,
        SUCCESS,
        FAILURE,
    }
}