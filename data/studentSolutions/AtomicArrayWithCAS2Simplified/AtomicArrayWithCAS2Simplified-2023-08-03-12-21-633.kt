@file:Suppress("UNCHECKED_CAST")

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
        val currentValue = array[index].value
        if (currentValue is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            val status = currentValue.status

            // if undecided state take previous
            val currentStatus = status.value
            if (currentStatus == Status.UNDECIDED || currentStatus == Status.FAILED) {
                if (currentValue.index1 == index) {
                    return currentValue.expected1 as E
                } else {
                    return currentValue.expected2 as E
                }
            }

            if (currentStatus == Status.SUCCESS) {
                if (currentValue.index1 == index) {
                    return currentValue.update1 as E
                } else {
                    return currentValue.update2 as E
                }
            }
        }

        return currentValue as E
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
        public val index1: Int,
        public val expected1: E,
        public val update1: E,
        public val index2: Int,
        public val expected2: E,
        public val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            val installed = installDescriptors()
            val completeResult = completeDescriptor(installed)
            removeDescriptors(completeResult)
        }

        private fun installDescriptors(): Boolean {
            while (true) {
                val currentLeft = array[index1].value
                if (currentLeft != this) {
                    if (currentLeft is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                        currentLeft.apply()
                        continue
                    }
                    if (!array[index1].compareAndSet(expected1, this)) {
                        return false
                    }
                }

                val currentRight = array[index2].value
                if (currentRight != this) {
                    if (currentRight is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                        currentRight.apply()
                        continue
                    }
                    if (!array[index2].compareAndSet(expected2, this)) {
                        return false
                    }
                }
                return true
            }
        }

        private fun completeDescriptor(success: Boolean): Status {
            val targetStatus = if (success) SUCCESS else FAILED
            return if (status.compareAndSet(UNDECIDED, targetStatus)) {
                targetStatus
            } else {
                status.value
            }
        }

        private fun removeDescriptors(status: Status) {
            if (status == SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            } else if (status == FAILED) {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
            } else {
                error("illegal state")
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}