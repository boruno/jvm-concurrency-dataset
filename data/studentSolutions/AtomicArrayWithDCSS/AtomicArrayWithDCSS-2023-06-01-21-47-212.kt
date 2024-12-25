//package day4

import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class AtomicArrayWithDCSS<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E? {
        // TODO: the cell can store a descriptor
        while (true) {
            when (val value = array[index].value) {
                null -> return null
                is AtomicArrayWithDCSS<*>.DCSSDescriptor -> value.applyOperation()
                else -> return value as E
            }
        }
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        // TODO: the cell can store a descriptor
        if (array[index].compareAndSet(expected, update))
            return true
        val value = array[index].value
        if (value is AtomicArrayWithDCSS<*>.DCSSDescriptor)
            value.applyOperation()
        return false
    }

    fun dcss(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO This implementation is not linearizable!
        // TODO Store a DCSS descriptor in array[index1].
        val descriptor = DCSSDescriptor(index1, expected1, update1, index2, expected2)
        descriptor.applyOperation()
        return descriptor.status.value != Status.UNDECIDED
    }

    private inner class DCSSDescriptor (
        val index1: Int, val expected1: E?, val updated1: E?,
        val index2: Int, val expected2: E?
    ) {
        val status = atomic(Status.UNDECIDED)

        fun applyOperation() {
            if (status.value == Status.UNDECIDED) {
                if (array[index1].compareAndSet(expected1, this) && array[index2].value == expected2)
                    status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
                status.compareAndSet(Status.UNDECIDED, Status.FAILED)
            }

            if (status.value == Status.FAILED)
                array[index1].compareAndSet(this, expected1)

            if (status.value == Status.SUCCESS)
                array[index1].compareAndSet(this, updated1)
        }
    }

    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}