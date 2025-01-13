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
            return if (value.status.value == SUCCESS) {
                if (index == value.index1) value.update1 as E else value.update2 as E
            } else {
                if (index == value.index1) value.expected1 as E else value.expected2 as E
            }
        }
        return value as E
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

            if (install() && updateStatus(SUCCESS)) {
                updateValues(this, true)
            } else {
                updateStatus(FAILED)
                updateValues(this, false)
            }
        }

        private fun install(): Boolean {
            return if (index1 > index2)
                installTo(index1, expected1) && installTo(index2, expected2)
            else
                installTo(index2, expected2) && installTo(index1, expected1)
        }

        private fun installTo(idx: Int, expected: E): Boolean {
            while (true) {
                when (val value = array[idx].value) {

/*
                    this -> when (status.value) {
                        UNDECIDED -> {
                            return true
                        }
                        SUCCESS -> {
                            updateValues(this, true)
                        }
                        FAILED -> {
                            updateValues(this, false)
                        }
                    }
*/

                    is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
                        when (value.status.value) {
                            UNDECIDED -> {
                                value.apply()
                            }
                            SUCCESS -> {
                                updateValues(value, true)
                            }
                            FAILED -> {
                                updateValues(value, false)
                            }
                        }
                    }

                    expected -> {
                        // DCSS must be here (ABA problem):
                        if (array[idx].compareAndSet(expected, this)) return true
                    }

                    else -> return false
                }
            }
        }

        private fun updateStatus(value: Status): Boolean {
            return status.compareAndSet(UNDECIDED, value)
        }
    }

    private fun updateValues(desc: AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor, success: Boolean) {
        if (success) {
            array[desc.index1].compareAndSet(desc, desc.update1)
            array[desc.index2].compareAndSet(desc, desc.update2)
        } else {
            array[desc.index1].compareAndSet(desc, desc.expected1)
            array[desc.index2].compareAndSet(desc, desc.expected2)
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}