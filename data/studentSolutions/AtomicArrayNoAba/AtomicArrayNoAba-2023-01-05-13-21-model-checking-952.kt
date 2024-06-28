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

    private fun applyDescriptor(descriptor: CAS2Descriptor<E>): Boolean {
        if (descriptor.status != CAS2Descriptor.Status.Updated) {
            val value1 = a[descriptor.index1].value
            val value2 = a[descriptor.index2].value

            if (value1 is CAS2Descriptor<*> && value1 != descriptor) {
                if (!applyDescriptor(value1 as CAS2Descriptor<E>)) {
                    return false
                }
            }

            if (value2 is CAS2Descriptor<*> && value2 != descriptor) {
                if (!applyDescriptor(value2 as CAS2Descriptor<E>)) {
                    return false
                }
            }

            if (value1 != descriptor) {
                if (!a[descriptor.index1].compareAndSet(descriptor.expected1, descriptor)) {
                    return false
                }
            }
            if (value2 != descriptor) {
                if (!a[descriptor.index2].compareAndSet(descriptor.expected2, descriptor)) {
                    a[descriptor.index1].compareAndSet(descriptor, descriptor.expected1)
                    return false
                }
            }
        }
        descriptor.casMarkUpdated()
        a[descriptor.index1].compareAndSet(descriptor, descriptor.update1)
        a[descriptor.index2].compareAndSet(descriptor, descriptor.update2)
        return true
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        val value = a[index].value
        if (value is CAS2Descriptor<*>) {
            if (!applyDescriptor(value as CAS2Descriptor<E>)) {
//                println("in cas ${Triple(index, expected, update)} with false")
                return false
            }
        }
        return a[index].compareAndSet(expected, update)
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        val descriptor = if (index1 < index2) {
            CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        } else if (index2 < index1) {
            CAS2Descriptor(index2, expected2, update2, index1, expected1, update1)
        } else {
            CAS2Descriptor(index2, expected2, update2, index1, expected1, update1)
        }
        return applyDescriptor(descriptor)
    }
}

private class CAS2Descriptor<E>(
    val index1: Int,
    val expected1: E,
    val update1: E,
    val index2: Int,
    val expected2: E,
    val update2: E
) {
    enum class Status {
        Undecided,
        Updated
    };

    private val _status = atomic(Status.Undecided)

    fun getValue(index: Int): E {
        when (_status.value) {
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
        _status.getAndSet(Status.Updated)
    }

    fun casMarkUpdated() = _status.compareAndSet(Status.Undecided, Status.Updated)

    val status get() = _status.value
}