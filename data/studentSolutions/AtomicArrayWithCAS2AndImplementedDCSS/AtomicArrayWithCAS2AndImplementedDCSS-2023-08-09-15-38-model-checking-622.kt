@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

package day3

import day3.AtomicArrayWithCAS2AndImplementedDCSS.Status.*
import java.util.concurrent.atomic.*

// This implementation never stores `null` values.
class AtomicArrayWithCAS2AndImplementedDCSS<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    fun get(index: Int): E {
        // TODO: the cell can store CAS2Descriptor
        val result = array.get(index)

        return when (result) {
            is AtomicArrayWithCAS2AndImplementedDCSS<*>.CAS2Descriptor -> result[index]
            else -> result
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
            var isSuccess = install()

            updateStatus(isSuccess)
            updateCells()
        }

        fun install(idx: Int, expected: E): Boolean {
            if (status.get() != UNDECIDED) return false

            while (true) {
                when (val result = array.get(idx)) {
                    expected -> {
                        if (array.compareAndSet(idx, expected, this)) {
                            return true
                        }
                    }
                    this -> return true
                    is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
                        result.apply()
                    }

                    else -> return false
                }
            }
            
            return true
        }

        fun install(): Boolean {
            return install(index1, expected1) && install(index2, expected2)
        }

        fun updateStatus(isSuccess: Boolean): Boolean {
            return status.compareAndSet(UNDECIDED, if (isSuccess) SUCCESS else FAILED)
        }

        fun updateCells(isSuccess: Boolean = status.get() == SUCCESS) {
            array.compareAndSet(index1, this, if (isSuccess) update1 else expected1)
            array.compareAndSet(index2, this, if (isSuccess) update2 else expected2)
        }

        operator fun get(index: Int): E {
            return if (status.get() == SUCCESS) {
                when (index) {
                    index1 -> update1
                    index2 -> update2
                    else -> error("Unexpected index $index")
                }
            } else {
                when (index) {
                    index1 -> expected1
                    index2 -> expected2
                    else -> error("Unexpected index $index")
                }
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }

    // TODO: Please use this DCSS implementation to ensure that
    // TODO: the status is `UNDECIDED` when installing the descriptor.
    // Double-Compare-Single-Set
    fun dcss(
        index: Int,
        expectedCellState: Any?,
        updateCellState: Any?,
        statusReference: AtomicReference<*>,
        expectedStatus: Any?
    ): Boolean =
        if (array[index] == expectedCellState && statusReference.get() == expectedStatus) {
            array[index] = updateCellState
            true
        } else {
            false
        }
}