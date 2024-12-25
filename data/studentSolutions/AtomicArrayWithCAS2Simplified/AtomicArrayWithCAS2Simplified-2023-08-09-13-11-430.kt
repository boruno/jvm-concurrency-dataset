@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray


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

        return when (val value = array[index]) {
            is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> when (value.status.get()) {
                SUCCESS -> value.updateForIndex(index)
                else -> value.expectedForIndex(index)
            }

            else -> value
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

        fun updateForIndex(index: Int): E = if (index == index1) update1 else update2
        fun expectedForIndex(index: Int): E = if (index == index1) expected1 else expected2

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.

            if (install()) {
                if (statusSuccess()) {
                    updateCells()
                }
            } else {
                statusFailed()
            }
        }

        private fun install(): Boolean {
            while (true) {
                val (cell1, cell2) = listOf(index1 to expected1, index2 to expected2).sortedBy { it.first }
                if (array.compareAndSet(cell1.first, cell1.second, this) || array.get(cell1.first) == this) {
                    return if (array.compareAndSet(cell2.first, cell2.second, this) || array.get(cell2.first) == this) {
                        true
                    } else {
                        array.set(cell1.first, cell1.second)
                        false
                    }
                } else {
                    if (!tryHelpForeignDescriptor(cell1.first)) {
                        return false
                    }
                }
            }
        }

        private fun statusSuccess(): Boolean {
            return status.compareAndSet(UNDECIDED, SUCCESS)
        }

        private fun statusFailed() {
            status.compareAndSet(UNDECIDED, FAILED)
        }

        private fun updateCells() {
            array.compareAndSet(index1, this, update1)
            array.compareAndSet(index2, this, update2)
        }

        private fun tryHelpForeignDescriptor(index: Int): Boolean {
            val descriptor = array.get(index)
            if (descriptor is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor && descriptor.status.get() == UNDECIDED) {
                descriptor.apply()
                return true
            }
            return false
        }

    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}