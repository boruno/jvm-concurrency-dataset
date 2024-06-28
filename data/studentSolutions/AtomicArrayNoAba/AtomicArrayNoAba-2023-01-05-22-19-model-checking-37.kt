import kotlinx.atomicfu.*
import javax.management.RuntimeErrorException

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

    private fun handleGoodOutcome(descriptor: CAS2Descriptor<E>) {
        descriptor.casMarkCompleted()
        a[descriptor.index1].compareAndSet(descriptor, descriptor.update1)
        a[descriptor.index2].compareAndSet(descriptor, descriptor.update2)
    }

    private fun handleBadOutcome(descriptor: CAS2Descriptor<E>) {
        a[descriptor.index1].compareAndSet(descriptor, descriptor.expected1)
        a[descriptor.index2].compareAndSet(descriptor, descriptor.expected2)
    }

    private fun applyDescriptor(descriptor: CAS2Descriptor<E>): Boolean {
        if (!tryPutDescriptorIntoCell(descriptor.index1, descriptor)) {
            return descriptor.isCompleted()
        }
        if (!tryPutDescriptorIntoCell(descriptor.index2, descriptor)) {
            if (descriptor.isCompleted()) {
                return true
            }
            descriptor.casMarkFailed()
            a[descriptor.index1].compareAndSet(descriptor, descriptor.expected1)
            return false
        }
        descriptor.casMarkCompleted()
        a[descriptor.index1].compareAndSet(descriptor, descriptor.update1)
        a[descriptor.index2].compareAndSet(descriptor, descriptor.update2)
        return true
    }


    fun cas(index: Int, expected: E, update: E): Boolean {
        val value = a[index].value
        if (value is CAS2Descriptor<*>) {
            if (!applyDescriptor(value as CAS2Descriptor<E>)) {
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
            throw Exception("lol")
        }
        return applyDescriptor(descriptor)
    }

    private fun finishOperationForIndex(index: Int, descriptor: CAS2Descriptor<E>) {
        when (descriptor.status) {
            CAS2Descriptor.Status.Undecided -> {
                if (index == descriptor.index1) {
                    if (!tryPutDescriptorIntoCell(index, descriptor)) {
                        if (descriptor.isCompleted()) {
                            return
                        }
                        descriptor.casMarkFailed()
                        a[descriptor.index1].compareAndSet(descriptor, descriptor.expected1)
                    } else {
                        handleGoodOutcome(descriptor)
                    }
                } else {
                    handleGoodOutcome(descriptor)
                }
            }

            CAS2Descriptor.Status.Completed -> {
                handleGoodOutcome(descriptor)
            }

            CAS2Descriptor.Status.Failed -> {
                handleBadOutcome(descriptor)
            }
        }
    }

    private fun tryPutDescriptorIntoCell(index: Int, descriptor: CAS2Descriptor<E>): Boolean {
        val value = a[index].value

        if (value is CAS2Descriptor<*>) {
            finishOperationForIndex(index, descriptor)
        }
        val expected = if (index == descriptor.index1) {
            descriptor.expected1
        } else {
            descriptor.expected2
        }
        a[index].compareAndSet(expected, descriptor)
        return a[index].value == descriptor
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
        Completed,
        Failed
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

            Status.Completed -> {
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

            else -> {
                return expected1
            }
        }
    }

    fun isCompleted() = _status.value == Status.Completed
    fun casMarkFailed() = _status.compareAndSet(Status.Undecided, Status.Completed)
    fun casMarkCompleted() = _status.compareAndSet(Status.Undecided, Status.Completed)

    val status get() = _status.value
}