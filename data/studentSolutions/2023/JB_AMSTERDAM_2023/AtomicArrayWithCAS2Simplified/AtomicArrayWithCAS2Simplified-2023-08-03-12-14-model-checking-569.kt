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

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
//            while (true) {
                installDescriptor()
                updateStatus()
                updateValues()
//            }
        }

        private fun installDescriptor() {
            val currentValue1 = array[index1].value
            if (array[index1].compareAndSet(expected1, this)) {
                array[index2].compareAndSet(expected2, this)
            } else if (currentValue1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                array[index2].compareAndSet(currentValue1.expected2, currentValue1)
            }
        }

        private fun updateStatus() {
            if (array[index1].value != this) {
                status.compareAndSet(UNDECIDED, FAILED)
                return
            }
            if (array[index2].value != this) {
                status.compareAndSet(UNDECIDED, FAILED)
                return
            }
            status.compareAndSet(UNDECIDED, SUCCESS)
        }

        private fun updateValues() {
            when (status.value) {
                FAILED -> {
                    array[index1].compareAndSet(this, expected1)
                    array[index2].compareAndSet(this, expected2)
                }
                SUCCESS -> {
                    array[index1].compareAndSet(this, update1)
                    array[index2].compareAndSet(this, update2)
                }
                else -> assert(false)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}