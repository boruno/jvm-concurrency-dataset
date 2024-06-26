import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any?>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int): E {
        val value = a[index].value
        return if (value is CAS2Descriptor<*>) {
            (value as CAS2Descriptor<E>).getValue(index)
        } else {
            value as E
        }
    }

    fun cas(index: Int, expected: E, update: E) =
        a[index].compareAndSet(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.

        val descriptor = CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        if (!a[index1].compareAndSet(expected1, descriptor)) {
            return false
        }
        if (!a[index2].compareAndSet(expected2, descriptor)) {
            a[index1].compareAndSet(descriptor, expected1)
            return false
        }
        descriptor.markUpdated()

        return a[index1].compareAndSet(descriptor, update1) && a[index2].compareAndSet(descriptor, update2)
    }
}

private class CAS2Descriptor<E>(
    private val index1: Int,
    private val expected1: E,
    private val update1: E,
    private val index2: Int,
    private val expected2: E,
    private val update2: E
) {
    private enum class Status {
        Undecided,
        Updated
    };

    private val status = atomic(Status.Undecided)

    fun getValue(index: Int): E {
        when (status.value) {
            Status.Undecided -> {
                return when (index) {
                    index1 -> {
                        expected1
                    }

                    index2 -> {
                        expected2
                    }

                    else -> {
                        expected1
                    }
                }
            }
            Status.Updated -> {
                return when (index) {
                    index1 -> {
                        update1
                    }

                    index2 -> {
                        update2
                    }

                    else -> {
                        update1
                    }
                }
            }
        }
    }

    fun markUpdated() {
        status.getAndSet(Status.Updated)
    }
}