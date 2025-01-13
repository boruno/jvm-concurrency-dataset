@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import AtomicArrayWithCAS2Simplified.Status.*
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
            install()
            if (applyLogically()) {
                updatePhysically()
            } else {
                rollbackPhysically()
            }
        }


        private fun install() {
            install(index1, expected1)
            if (status.get() == FAILED) return
            install(index2, expected2)
        }

        private fun install(index: Int, expected: E) {
            if (!array.compareAndSet(index, expected, this)) {
                val cell = array[index]
                if (cell is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    if (cell != this) {
                        cell.status.compareAndSet(UNDECIDED, SUCCESS)
                    }
                    // TODO: help
                } else {
                    status.set(FAILED)
                }
            }
        }

        private fun applyLogically(): Boolean {
            return status.compareAndSet(UNDECIDED, SUCCESS) || status.get() == SUCCESS
        }

        private fun updatePhysically() {
            array.compareAndSet(index1, this, update1)
            array.compareAndSet(index2, this, update2)
        }

        private fun rollbackPhysically() {
            array.compareAndSet(index1, this, expected1)
            array.compareAndSet(index2, this, expected2)
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}