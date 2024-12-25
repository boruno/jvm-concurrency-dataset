@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
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
        val state = array[index]
        return if (state is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            if (state.status.get() == AtomicArrayWithCAS2SingleWriter.Status.SUCCESS) {
                if (state.index1 == index) {
                    state.update1
                } else {
                    state.update2
                }
            } else {
                if (state.index1 == index) {
                    state.expected1
                } else {
                    state.expected2
                }
            }
        } else {
            state
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

            val installDescriptor = installDescriptor()
            applyLogically(installDescriptor)
            physicallyApply()
        }

        private fun installDescriptor(): Boolean {
            val firstCell = array.compareAndSet(index1, expected1, this) ||
                    array.get(index1) == expected1
            val secondCell = array.compareAndSet(index2, expected2, this) ||
                    array.get(index2) == expected2
            return firstCell && secondCell
        }
        private fun applyLogically(installedDescriptor: Boolean) {
            if (installedDescriptor)
                status.compareAndSet(
                    UNDECIDED,
                    SUCCESS
                )
            else
                status.compareAndSet(
                    UNDECIDED,
                    FAILED
                )
        }


        private fun physicallyApply() {
            if (status.get() == SUCCESS) {
                array.compareAndSet(index1, this, update1)
                array.compareAndSet(index2, this, update2)
            } else {
                array.compareAndSet(index1, this, expected1)
                array.compareAndSet(index2, this, expected2)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}