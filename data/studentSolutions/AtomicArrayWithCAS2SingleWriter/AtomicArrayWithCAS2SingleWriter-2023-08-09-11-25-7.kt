@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import day3.AtomicArrayWithCAS2SingleWriter.Status.*
import java.util.concurrent.atomic.*

// This implementation never stores `null` values.
class AtomicArrayWithCAS2SingleWriter<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    fun get(index: Int): E {
        // TODO: the cell can store CAS2Descriptor
        if (array[index] is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            val descriptor = array[index] as AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor
            return descriptor.getValueForIndex(index) as E
        }

        return array[index] as E
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
        return descriptor.status.get() === SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int, val expected1: E, val update1: E,
        val index2: Int, val expected2: E, val update2: E
    ) {
        val status = AtomicReference(UNDECIDED)

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            // TODO: In this task, only one thread can call cas2(..),
            // TODO: so cas2(..) calls cannot be executed concurrently.
            if (installDescriptor()) {
                updateStatusToSuccess()
            } else {
                updateStatusToFail()
            }

            updateCells()
        }

        private fun installDescriptor(): Boolean {
            return (
                array.compareAndSet(index1, expected1, this) &&
                array.compareAndSet(index2, expected2, this)
            )
        }

        private fun updateStatusToFail() {
            status.compareAndSet(UNDECIDED, FAILED)
        }

        private fun updateStatusToSuccess() {
            status.compareAndSet(UNDECIDED, SUCCESS)
        }

        private fun updateCells() {
            if (status.get() == SUCCESS) {
                array.compareAndSet(index1, this, update1)
                array.compareAndSet(index2, this, update2)
            } else {
                array.compareAndSet(index1, this, expected1)
                array.compareAndSet(index2, this, expected2)
            }
        }

        fun getValueForIndex(i: Int): E {
            return if (status.get() == SUCCESS) {
                getUpdatedForIndex(i)
            } else {
                getExpectedForIndex(i)
            }
        }

        private fun getUpdatedForIndex(i: Int): E {
            return if (i == index1) {
                return update1
            } else {
                update2
            }
        }

        private fun getExpectedForIndex(i: Int): E {
            return if (i == index1) {
                return expected1
            } else {
                expected2
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}