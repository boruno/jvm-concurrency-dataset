//package day3

import AtomicArrayWithCAS2Simplified.Status.*
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
            return when (value.status.value) {
                SUCCESS -> if (value.index1 == index) value.update1 as E else value.update2 as E
                UNDECIDED, FAILED -> if (value.index1 == index) value.expected1 as E else value.expected2 as E
            }
        }

        return value as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }

        val descriptor = if (index1 < index2) {
            CAS2Descriptor(
                index1 = index1, expected1 = expected1, update1 = update1,
                index2 = index2, expected2 = expected2, update2 = update2
            )
        } else {
            CAS2Descriptor(
                index1 = index2, expected1 = expected2, update1 = update2,
                index2 = index1, expected2 = expected1, update2 = update1
            )
        }

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
            if (installDescriptorToCell(index1, expected1)) installDescriptorToCell(index2, expected2)
            updateStatus()
            updateCells()
        }

        private fun installDescriptorToCell(i: Int, exp: Any): Boolean {
            while (true) {
                if (status.value != UNDECIDED) return true
                val cellValue = array[i].value
                if (cellValue == this) return true
                if (cellValue == exp) {
                    if (array[i].compareAndSet(exp, this)) return true
                }
                if (cellValue is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    if (cellValue.status.value == UNDECIDED) {
                        val anotherIndex = if (i == cellValue.index1) cellValue.index2 else cellValue.index1
                        val anotherExpected = if (i == cellValue.index1) cellValue.expected2 else cellValue.expected1
                        if (cellValue.installDescriptorToCell(anotherIndex, anotherExpected)) {
                            cellValue.updateStatus()
                        }
                    }
                    if (cellValue.status.value == SUCCESS) {
                        val upd = if (cellValue.index1 == i) cellValue.update1 else cellValue.update2
                        array[i].compareAndSet(cellValue, upd)
                    }
                    if (cellValue.status.value == FAILED) {
                        val expected = if (i == cellValue.index1) cellValue.expected1 else cellValue.expected2
                        array[i].compareAndSet(cellValue, expected)
                    }
                    return array[i].compareAndSet(exp, this)
                }
                if (cellValue != exp) {
                    status.compareAndSet(UNDECIDED, FAILED)
                    return false
                }
            }


        }

        private fun updateStatus() {
            if (status.value == UNDECIDED && get(index1) == expected1 && get(index2) == expected2) status.compareAndSet(
                UNDECIDED,
                SUCCESS
            )
            else status.compareAndSet(UNDECIDED, FAILED)
        }

        private fun updateCells() {
            when (status.value) {
                SUCCESS -> {
                    array[index1].compareAndSet(this, update1)
                    array[index2].compareAndSet(this, update2)
                }

                FAILED, UNDECIDED -> {
                    array[index1].compareAndSet(this, expected1)
                    array[index2].compareAndSet(this, expected2)
                }
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}