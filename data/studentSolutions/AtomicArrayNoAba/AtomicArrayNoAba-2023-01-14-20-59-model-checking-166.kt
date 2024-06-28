import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    // It was necessary to change it to 'Any' because of CasnDescriptor.
    private val a = atomicArrayOfNulls<Any>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int): E {
        if (a[index].value is CasnDescriptor<*, *>) {
            val descriptor = a[index].value as CasnDescriptor<*, *>
            if (descriptor.outcome.compareAndSet(CasnOutcome.UNDEFINED, CasnOutcome.SUCCESS)) {
                a[descriptor.indexA].value = descriptor.updateA
                a[descriptor.indexB].value = descriptor.updateB
            } else {
                a[descriptor.indexA].value = descriptor.expectedA
                a[descriptor.indexB].value = descriptor.expectedB
            }
        }

        return a[index].value as E
    }

    fun cas(index: Int, expected: E, update: E) =
        a[index].compareAndSet(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        val descriptor = CasnDescriptor(index1, index2, expected1, expected2, update1, update2)

        if (!a[index1].compareAndSet(descriptor.expectedA, descriptor))
            return false

        if (!a[index2].compareAndSet(descriptor.expectedB, descriptor)) {
            a[index1].value = expected1
            descriptor.outcome.compareAndSet(CasnOutcome.UNDEFINED, CasnOutcome.FAILED)
            return false
        }

        // Point of the decision-making.
        descriptor.outcome.compareAndSet(CasnOutcome.UNDEFINED, CasnOutcome.SUCCESS)

        a[index1].value = descriptor.updateA
        a[index2].value = descriptor.updateB

        return true
    }
}

private class CasnDescriptor<A, B>(
    val indexA: Int,
    val indexB: Int,
    val expectedA: A,
    val expectedB: B,
    val updateA: A,
    val updateB: B) {
    val outcome: AtomicRef<CasnOutcome> = atomic(CasnOutcome.UNDEFINED)
}

private enum class CasnOutcome {
    UNDEFINED,
    SUCCESS,
    FAILED,
}