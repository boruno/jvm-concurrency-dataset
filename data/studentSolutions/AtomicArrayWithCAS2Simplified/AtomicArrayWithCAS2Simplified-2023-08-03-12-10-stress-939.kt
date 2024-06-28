package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.*


// This implementation never stores `null` values.
class AtomicArrayWithCAS2Simplified<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E {
        // TODO: the cell can store CAS2Descriptor
        val value = array[index].value
        if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            if (index == value.index1)
                return (if (value.status.value == SUCCESS) value.update1 else value.expected1) as E
            return (if (value.status.value == SUCCESS) value.update2 else value.expected2) as E
        }
        return value as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = if (index1 < index2) CAS2Descriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2, update2 = update2
        ) else CAS2Descriptor(
            index1 = index2, expected1 = expected2, update1 = update2,
            index2 = index1, expected2 = expected1, update2 = update1
        )
        descriptor.apply()
        return descriptor.status.value === SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.

            if (array[index1].compareAndSet(expected1, this)) {
                if (array[index2].compareAndSet(expected2, this)) {
                    status.compareAndSet(UNDECIDED, SUCCESS)
                    array[index1].compareAndSet(this, update1)
                    array[index2].compareAndSet(this, update2)
                } else {
                    status.compareAndSet(UNDECIDED, FAILED)
                    array[index1].compareAndSet(this, expected1)
                }
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}