//package day3

import day3.AtomicArrayWithCAS2SingleWriter.Status.*
import kotlinx.atomicfu.*
import kotlin.math.absoluteValue

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
            if (cellValue.status.value === SUCCESS) {
                return if (index == cellValue.index1.absoluteValue) cellValue.update1 as E else cellValue.update2 as E
            } else {
                return if (index == cellValue.index1.absoluteValue) cellValue.expected1 as E else cellValue.expected2 as E
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

            installDescriptor()
            if (this.status.value === FAILED) {
                return
            }

            updateStatus()
            if (this.status.value === FAILED) {
                return
            }

            success()
        }

        private fun installDescriptor() {
            if (!array[index1].compareAndSet(expected1, this)) {
                this.status.compareAndSet(UNDECIDED, FAILED)
                return
            }

            if (!array[index2].compareAndSet(expected2, this)) {
                this.status.compareAndSet(UNDECIDED, FAILED)
                return
            }
        }

        private fun updateStatus() {
            if (!array[index1].compareAndSet(expected1, update2)) {
                this.status.compareAndSet(UNDECIDED, FAILED)
                return
            }

            if (!array[index2].compareAndSet(expected2, update2)) {
                this.status.compareAndSet(UNDECIDED, FAILED)
                return
            }
        }

        private fun success() {
            this.status.compareAndSet(UNDECIDED, SUCCESS)
//            if (!this.status.compareAndSet(UNDECIDED, SUCCESS)) {
//                this.status.compareAndSet(this.status.value, FAILED)
//            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}