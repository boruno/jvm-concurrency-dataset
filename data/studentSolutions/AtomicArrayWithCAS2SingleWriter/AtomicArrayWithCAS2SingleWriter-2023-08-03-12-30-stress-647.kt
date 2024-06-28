package day3

import day3.AtomicArrayWithCAS2SingleWriter.Status.*
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

        val value = array[index].value

        if (value is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
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
            // TODO: In this task, only one thread can call cas2(..),
            // TODO: so cas2(..) calls cannot be executed concurrently.

            if (install() && updateStatus(SUCCESS)) {
                updateValues()
            } else {
                updateStatus(FAILED)
            }
        }

        private fun install(): Boolean {
            return installTo(index1) && installTo(index2)
        }

        private fun installTo(idx: Int): Boolean {
            val value = array[idx].value
            return if (value is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
                array[idx].value = this
                true
       /*         if (value.status.value == SUCCESS) {
                    array[idx].compareAndSet(value.update1, this)
                } else {
                    array[idx].compareAndSet(value.expected1, this)
                }*/
            } else {
                array[idx].compareAndSet(expected1, this)
            }
        }

        private fun updateStatus(value: Status): Boolean {
            return status.compareAndSet(UNDECIDED, value)
        }

        private fun updateValues() {
            array[index1].value = update1
            array[index2].value = update2
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}