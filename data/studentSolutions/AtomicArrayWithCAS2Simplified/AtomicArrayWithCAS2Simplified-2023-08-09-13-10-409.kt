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
        while (true) {
            if (cell is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                when (cell.status.get()) {
                    UNDECIDED, FAILED -> if (index == cell.index1) cell.expected1 else cell.expected2
                    SUCCESS -> if (index == cell.index1) cell.update1 else cell.update2
                }
//                cell.apply()
            } else {
                return cell as E
            }
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
        return descriptor.status.get() === SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = AtomicReference(UNDECIDED)

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            if (status.get() == UNDECIDED)
                install()
            if (applyLogically()) {
                updatePhysically()
            } else {
                rollbackPhysically()
            }
        }


        private fun install() {
            if (index1 < index2) {
                install(index1, expected1, update1)
                install(index2, expected2, update2)
            } else {
                install(index2, expected2, update2)
                install(index1, expected1, update1)
            }
        }

        private fun install(index: Int, expected: E, update: E) {
            if (!array.compareAndSet(index, expected, this)) {
                val cell = array[index]
                if (cell is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    if (cell != this) {
                        cell.apply()
                    }
                } else {
                    status.compareAndSet(UNDECIDED, FAILED)
                }
            }
        }

        private fun applyLogically(): Boolean {
            return status.compareAndSet(UNDECIDED, SUCCESS) || status.compareAndSet(SUCCESS, SUCCESS)
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