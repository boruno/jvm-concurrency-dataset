package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.*
import kotlin.math.max
import kotlin.math.min


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
        val value = array[index].value!!
        val result: E = if (value is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            value.getValue(index) as E
        } else {
            return value as E
        }
        return result
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = CAS2Descriptor(
            idx1 = index1, exp1 = expected1, upd1 = update1,
            idx2 = index2, exp2 = expected2, upd2 = update2
        )
        descriptor.apply()
        return descriptor.status.value === SUCCESS
    }

    inner class CAS2Descriptor(
        idx1: Int,
        exp1: E,
        upd1: E,
        idx2: Int,
        exp2: E,
        upd2: E
    ) {
        val status = atomic(UNDECIDED)

        private val index1: Int
        private val expected1: E
        private val update1: E
        private val index2: Int
        private val expected2: E
        private val update2: E

        init {
            if (idx1 < idx2) {
                index1 = idx1
                expected1 = exp1
                update1 = upd1
                index2 = idx2
                expected2 = exp2
                update2 = upd2
            } else {
                index1 = idx2
                expected1 = exp2
                update1 = upd2
                index2 = idx1
                expected2 = exp1
                update2 = upd1
            }
        }

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            install()
            updateStatus()
            updateCells()
        }

        private fun install() {
            // any of them true
            // help the others
            // while (descriptor1) help
            // while (descriptor2) help

            if (!array[index1].compareAndSet(expected1, this)) {
                var descriptor1 = array[index1].value
                while (descriptor1 != this && descriptor1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    descriptor1.apply()
                    descriptor1 = array[index1].value
                }
                array[index1].compareAndSet(expected1, this)
            }


            if (!array[index2].compareAndSet(expected2, this)) {
                var descriptor2 = array[index2].value
                while (descriptor2 != this && descriptor2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    descriptor2.apply()
                    descriptor2 = array[index2].value
                }
                array[index2].compareAndSet(expected2, this)
            }
        }

        private fun updateStatus() {
            // can be already success
            if (array[index1].value == this && array[index2].value == this) {
                status.compareAndSet(UNDECIDED, SUCCESS)
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }
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