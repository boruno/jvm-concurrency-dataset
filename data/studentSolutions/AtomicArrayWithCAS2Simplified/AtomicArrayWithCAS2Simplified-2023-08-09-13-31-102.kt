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
        val cell = array[index]
        return if (cell is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            when (cell.status.get()) {
                SUCCESS -> cell.getUpdateValue(index)
                else -> cell.getExpectedValue(index)
            }
        } else {
            cell
        } as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = if (index1 < index2) {
            CAS2Descriptor(
                index1 = index1, expected1 = expected1, update1 = update1,
                index2 = index2, expected2 = expected2, update2 = update2
            )
        } else {
            CAS2Descriptor(
                index1 = index2, expected1 = expected2, update1 = update2,
                index2 = index1, expected2 = expected1, update2 = update1
            )
        }
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

        fun getExpectedValue(index: Int) = if (index == index1) expected1 else expected2
        fun getUpdateValue(index: Int) = if (index == index1) update1 else update2

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.


            val success = install()
            updateStatus(success)
            updatePhysically()
        }

        private fun install(): Boolean {
            if (!install(index1, expected1)) return false
            return install(index2, expected2)
        }

        private fun install(index: Int, expected: E): Boolean {
            if (status.get() != UNDECIDED) return false
            while (true) {
                val cell = array[index]
                when (cell) {
                    this -> {
                        return true
                    }
                    is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
                        cell.apply()
                    }
                    expected -> {
                        return array.compareAndSet(index, expected, this)
                    }
                    else -> return false
                }
            }
        }

        private fun updateStatus(success: Boolean) {
            status.compareAndSet(UNDECIDED, if (success) SUCCESS else FAILED)
        }

        private fun applyLogically(): Boolean {
            return status.compareAndSet(UNDECIDED, SUCCESS) || status.compareAndSet(SUCCESS, SUCCESS)
        }

        private fun updatePhysically() {
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