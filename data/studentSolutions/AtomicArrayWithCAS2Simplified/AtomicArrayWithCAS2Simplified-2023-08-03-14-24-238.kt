//package day3

import AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.*


// This implementation never stores `null` values.
class AtomicArrayWithCAS2Simplified<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)
    private val helpingMode = atomic(false)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E {
        // TODO: the cell can store CAS2Descriptor
        val value = array[index].value as E
        if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            when (value.status.value) {
                SUCCESS -> return value.getUpdatedValue(index) as E
                FAILED -> return value.getOldValues(index) as E
                UNDECIDED -> {
                    return value.getOldValues(index) as E
                }
            }

        }
        return value
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = if (index1 > index2) {
            CAS2Descriptor(
                index1 = index2, expected1 = expected2, update1 = update2,
                index2 = index1, expected2 = expected1, update2 = update1
            )
        } else {
            CAS2Descriptor(
                index1 = index1, expected1 = expected1, update1 = update1,
                index2 = index2, expected2 = expected2, update2 = update2
            )
        }
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

        fun apply(helpingMode: Boolean = true) {
            install(this)
            if (helpingMode) {
                help()
                install(this)
            }
                updateStatus()
                setValues(this)
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
        }

        private fun install(descriptor: CAS2Descriptor) {
                if (status.value == UNDECIDED) {
                    array[index1].compareAndSet(expected1, descriptor)
                    if (array[index1].value == this) {
                        array[index2].compareAndSet(expected2, descriptor)
                    }
                }
        }

        private fun updateStatus() {
            if (array[index1].value == this && array[index2].value == this) {
                status.compareAndSet(UNDECIDED, SUCCESS)
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }
        }
        private fun help() {
            val cell = array[index1].value
            if (cell != this && cell is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                cell.apply(false)
            }
            val cell2 = array[index2].value
            if (cell2 != this && cell2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                cell2.apply(false)
            }
        }

        private fun setValues(descriptor: CAS2Descriptor) {
            if (status.value == SUCCESS) {

            array[index1].compareAndSet(descriptor, update1)
            array[index2].compareAndSet(descriptor, update2)
            } else {
                array[index1].compareAndSet(descriptor, expected1)
                array[index2].compareAndSet(descriptor, expected2)
            }
        }

        fun getUpdatedValue(index: Int): E {
            if (index1 == index) return update1
            if (index2 == index) return update2
            error("")
        }

        fun getOldValues(index: Int): E {
            if (index1 == index) return expected1
            if (index2 == index) return expected2
            error("")
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}