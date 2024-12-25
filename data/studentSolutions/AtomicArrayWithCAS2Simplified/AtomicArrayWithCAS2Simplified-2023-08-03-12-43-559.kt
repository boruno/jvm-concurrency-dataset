//package day3

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
        val cellState = array[index].value
        if (cellState is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            return cellState.get(index) as E
        }
        return cellState as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = CAS2Descriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2, update2 = update2
        )
        descriptor.apply()
        return descriptor.status.value === SUCCESS
    }

    inner class CAS2Descriptor(
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E,
        private val update2: E
    ) {
        val status = atomic(UNDECIDED)

        private fun installDescriptor() {
            if (status.value != UNDECIDED) {
                return
            }

            val firstIndex = if (index1 < index2) index1 else index2
            val secondIndex = if (index1 < index2) index2 else index1
            val firstExpected = if (index1 < index2) expected1 else expected2
            val secondExpected = if (index1 < index2) expected2 else expected1

            val firstCell = array[firstIndex].value
            val secondCell = array[secondIndex].value

            if (firstCell is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor && firstCell != this) {
                firstCell.apply()
            }

            if (secondCell is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor && secondCell != this) {
                secondCell.apply()
            }

            if (array[firstIndex].compareAndSet(firstExpected, this)) {
                array[secondIndex].compareAndSet(secondExpected, this)
            }
        }

        private fun updateStatus() {
            val firstCell = array[index1].value
            val secondCell = array[index2].value

            if (firstCell == this && secondCell == this) {
                status.compareAndSet(UNDECIDED, SUCCESS)
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }
        }

        private fun updateCells() {
            val curStatus = status.value
            if (curStatus == SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            } else if (curStatus == FAILED) {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
            }
        }

        fun get(index: Int): E {
            if (status.value === SUCCESS) {
                return if (index == index1) update1 else update2
            } else {
                return if (index == index1) expected1 else expected2
            }
        }

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            installDescriptor()
            updateStatus()
            updateCells()
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}