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
        if (value is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            return when (value.status.value) {
                AtomicArrayWithCAS2SingleWriter.Status.UNDECIDED, AtomicArrayWithCAS2SingleWriter.Status.FAILED -> when (index) {
                    value.index1 -> value.expected1
                    value.index2 -> value.expected2
                    else -> assert(false)
                }
                AtomicArrayWithCAS2SingleWriter.Status.SUCCESS ->  when (index) {
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
        val descriptor = if (index1 > index2) {
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
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E,
        private val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            while (true) {
                val descriptor = installDescriptor()
                updateStatus(descriptor)
                updateValues(descriptor)
                if (descriptor == this) break
            }
        }

        private fun installDescriptor(): CAS2Descriptor {
            val currentValue1 = array[index1].value
            if (currentValue1 == this) {
                array[index2].compareAndSet(expected2, this)
            } else if (array[index1].compareAndSet(expected1, this)) {
                val currentValue2 = array[index2].value
                if (!array[index2].compareAndSet(expected2, this) && currentValue2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    return currentValue2 as AtomicArrayWithCAS2Simplified<E>.CAS2Descriptor
                }
                return this
            } else if (currentValue1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                array[index2].compareAndSet(currentValue1.expected2, currentValue1)
                return currentValue1 as AtomicArrayWithCAS2Simplified<E>.CAS2Descriptor
            }
            return this
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