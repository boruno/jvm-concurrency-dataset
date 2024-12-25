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
        if(currentValue is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            val status = currentValue.status

            // if undecided state take previous
            val currentStatus = status.value
            if(currentStatus == AtomicArrayWithCAS2Simplified.Status.UNDECIDED || currentStatus == AtomicArrayWithCAS2Simplified.Status.FAILED) {
                if(currentValue.index1 == index) {
                    return currentValue.expected1 as E
                } else {
                    return currentValue.expected2 as E
                }
            }

            if(currentStatus == AtomicArrayWithCAS2Simplified.Status.SUCCESS) {
                if(currentValue.index1 == index) {
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
        val descriptor = CAS2Descriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2, update2 = update2
        )
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
            completeDescriptor(installed)
            removeDescriptors(installed)
        }

        private fun installDescriptors(): Boolean {
            if (array[index1].compareAndSet(expected1, this) &&
                array[index2].compareAndSet(expected2, this)
            ) {
                return true
            }
            return false
        }

        private fun completeDescriptor(success: Boolean) {
            if (success) {
                status.compareAndSet(
                    UNDECIDED,
                    SUCCESS
                )
            } else {
                status.compareAndSet(
                    UNDECIDED,
                    FAILED
                )
            }
        }

        private fun removeDescriptors(success: Boolean) {
            if (success) {
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