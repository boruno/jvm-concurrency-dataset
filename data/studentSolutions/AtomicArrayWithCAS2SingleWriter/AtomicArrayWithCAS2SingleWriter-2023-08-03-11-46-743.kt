//package day3

import AtomicArrayWithCAS2SingleWriter.Status.*
import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class AtomicArrayWithCAS2SingleWriter<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E {
        // TODO: the cell can store CAS2Descriptor
        val cellValue = array[index].value as E

        if (cellValue is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            if (cellValue.status.value == SUCCESS) {
                return cellValue.update1 as E
            } else {
                return cellValue.expected1 as E
            }
        } else {
            return cellValue
        }
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
        val expected1: E,
        val update1: E,
        private val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            // TODO: In this task, only one thread can call cas2(..),
            // TODO: so cas2(..) calls cannot be executed concurrently.

            // 1. Install descriptor
            val cell1 = array[index1]
            val cell2 = array[index2]

            installDescriptor()
            updateStatus()
            applyStatus(SUCCESS)




//            if (cell1.compareAndSet(cell1.value, this)) {
//                if (cell2.compareAndSet(cell2.value, this)) {
//
//                    if (cell1.compareAndSet(this.expected1, this.update1)) {
//                        if (cell2.compareAndSet(this.expected2, this.update2)) {
//                            this.status.compareAndSet(UNDECIDED, SUCCESS)
//                        }
//                    }
//                }
//            }
//            else {
//                this.status.compareAndSet(UNDECIDED, FAILED)
//            }
        }

        private fun installDescriptor() {
            array[index1].value = this
            array[index2].value = this
        }

        private fun updateStatus() {
            val cell1Value = array[index1].value
            val cell2Value = array[index2].value

            if (status.value != FAILED) {
                array[index1].value = this.update1
                array[index2].value = this.update2
            }
        }

        private fun applyStatus(to: Status) {
            this.status.compareAndSet(UNDECIDED, to)
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}