package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls


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
        val value = array[index].value
        if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            return when {
                (index == value.index1) && (value.status.value == SUCCESS) -> value.update1
                (index == value.index1) -> value.expected1
                (index == value.index2) && (value.status.value == SUCCESS) -> value.update2
                (index == value.index2) -> value.expected2
                else -> error("unapplicable descriptor")
            } as E
        }
        return value as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }

        val descriptor = if (index1 > index2)
            CAS2Descriptor(
                index1 = index1, expected1 = expected1, update1 = update1,
                index2 = index2, expected2 = expected2, update2 = update2
            )
        else
            CAS2Descriptor(
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
            if (installAt(index1, expected1) && installAt(index2, expected2)) {
                updateStatus(SUCCESS)
            } else {
                updateStatus(FAILED)
            }
            updateCells()
        }

        private fun installAt(cell: Int, expecting: E): Boolean {
            while (status.value == UNDECIDED) {
                val value = array[cell].value
                if (value == this) return true
                if (value !is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor && array[cell].value == value) {
                    if (value == expecting && array[cell].value == value) {
                        array[cell].compareAndSet(expecting, this)
                        return true
                    } else if (array[cell].value == value) return false
                }
                (array[cell].value as? AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor)?.apply()
            }
            return false
        }

        private fun updateStatus(to: Status) {
            status.compareAndSet(UNDECIDED, to)
        }

        private fun updateCells() {
            if (status.value == SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            } else {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}