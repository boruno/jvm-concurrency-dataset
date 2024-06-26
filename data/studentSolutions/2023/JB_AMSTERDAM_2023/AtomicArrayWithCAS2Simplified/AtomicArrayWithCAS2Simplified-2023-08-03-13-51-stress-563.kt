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
            return when (value.status.value) {
                UNDECIDED, FAILED -> when (index) {
                    value.index1 -> value.expected1
                    value.index2 -> value.expected2
                    else -> assert(false)
                }
                SUCCESS ->  when (index) {
                    value.index1 -> value.update1
                    value.index2 -> value.update2
                    else -> assert(false)
                }
            } as E
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
            installDescriptor()
            updateStatus(this)
            updateValues(this)
        }

        private fun installDescriptor() {
            val currentValue1 = array[index1].value
            val currentValue2 = array[index2].value
            if (!array[index1].compareAndSet(expected1, this)) {
                if (currentValue1 != this && currentValue1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    if (currentValue1.status.value != SUCCESS) {
                        currentValue1.apply()
                    }
                }
            }
            if (!array[index2].compareAndSet(expected2, this)) {
                if (currentValue2 != this && currentValue2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    if (currentValue2.status.value != SUCCESS) {
                        currentValue2.apply()
                    }
                }
            }
        }

        private fun updateStatus(descriptor: CAS2Descriptor) {
            if (array[descriptor.index1].value != descriptor) {
                descriptor.status.compareAndSet(UNDECIDED, FAILED)
                return
            }
            if (array[descriptor.index2].value != descriptor) {
                descriptor.status.compareAndSet(UNDECIDED, FAILED)
                return
            }
            descriptor.status.compareAndSet(UNDECIDED, SUCCESS)
        }

        private fun updateValues(descriptor: CAS2Descriptor) {
            when (descriptor.status.value) {
                FAILED -> {
                    array[descriptor.index1].compareAndSet(descriptor, descriptor.expected1)
                    array[descriptor.index2].compareAndSet(descriptor, descriptor.expected2)
                }
                SUCCESS -> {
                    array[descriptor.index1].compareAndSet(descriptor, descriptor.update1)
                    array[descriptor.index2].compareAndSet(descriptor, descriptor.update2)
                }
                else -> assert(false)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}