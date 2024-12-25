@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import day3.AtomicArrayWithCAS2SingleWriter.Companion.fromDescriptorOrValue
import java.util.concurrent.atomic.*


// This implementation never stores `null` values.
class AtomicArrayWithCAS2Simplified<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    fun get(index: Int): E {
        // TODO: the cell can store CAS2Descriptor
        val maybeDescriptor = array[index]!!
        return maybeDescriptor.fromDescriptorOrValue {
            if (status.get() === AtomicArrayWithCAS2SingleWriter.Status.SUCCESS) {
                if (index == index1) update1 else update2
            }
            else {
                if (index == index1) expected1 else expected2
            }
        } as E
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
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E,
        private val update2: E
    ) {
        val status = AtomicReference(UNDECIDED)

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            installDescriptor()
            updateStatus()
            updateCells()
        }

        private fun updateCells() {
            array.compareAndSet(index1, this, if (status.get() == SUCCESS) update1 else expected1)
            array.compareAndSet(index2, this, if (status.get() == SUCCESS) update2 else expected2)
        }

        private fun updateStatus() {
            if (array[index1]?.fromDescriptorOrValue { expected1 } != expected1
                || array[index2]?.fromDescriptorOrValue { expected2 } != expected2) {
                status.compareAndSet(UNDECIDED, FAILED)
            }
            status.compareAndSet(UNDECIDED, SUCCESS)
        }

        private fun installDescriptor() {
            array.compareAndSet(index1, expected1, this)
            array.compareAndSet(index2, expected2, this)
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}