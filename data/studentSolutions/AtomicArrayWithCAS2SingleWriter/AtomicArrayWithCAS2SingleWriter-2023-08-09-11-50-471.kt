@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import AtomicArrayWithCAS2SingleWriter.Status.*
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

    fun get(index: Int): E = when (val item = array[index]) {
        is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor -> item.getDesciptorCell(index) as E
        else -> array[index] as E
        // TODO: the cell can store CAS2Descriptor
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
            val installed = install()
            updateStatus(installed)
            updateCells()
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            // TODO: In this task, only one thread can call cas2(..),
            // TODO: so cas2(..) calls cannot be executed concurrently.
        }

        private fun install(): Boolean =
            installCell(index1, expected1) && installCell(index2, expected2)

        private fun installCell(cellIndex: Int, expected: E): Boolean =
            array.compareAndSet(cellIndex, expected, this)

        fun getDesciptorCell(
            cellIndex: Int
        ) = if (status.get() == SUCCESS) {
            if (cellIndex == index1) {
                update1
            } else {
                update2
            }
        } else {
            if (cellIndex == index1) {
                expected1
            } else { expected2 }
        }

        private fun updateStatus(installed: Boolean) = if (installed) {
            status.compareAndSet(UNDECIDED, SUCCESS)
        } else {
            status.compareAndSet(UNDECIDED, FAILED)
        }

        private fun updateCells() {
            when (status.get()) {
                SUCCESS -> {
                    array.compareAndSet(index1, this, update1)
                    array.compareAndSet(index2, this, update2)
                }
                FAILED -> {
                    array.compareAndSet(index1, this, expected1)
                    array.compareAndSet(index2, this, expected2)
                }
                else -> {}
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}