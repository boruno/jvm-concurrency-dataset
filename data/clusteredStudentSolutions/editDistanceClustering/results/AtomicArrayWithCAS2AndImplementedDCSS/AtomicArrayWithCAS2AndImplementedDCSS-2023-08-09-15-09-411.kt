@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import AtomicArrayWithCAS2AndImplementedDCSS.Status.*
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

    fun get(index: Int): E = when (val item = array[index]) {
        // the cell can store CAS2Descriptor
        is AtomicArrayWithCAS2AndImplementedDCSS<*>.CAS2Descriptor -> item.getDescriptorCell(index) as E
        else -> item as E
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
            // Install the descriptor;
            val installed = install()
            // update the status
            updateStatus(installed)
            // and update the cells
            updateCells()
        }

        private fun install(): Boolean = if (index1 < index2) {
            installFirstCell() && installSecondCell()
        } else {
            installSecondCell() && installFirstCell()
        }

        private fun installFirstCell() = installCell(index1, expected1)
        private fun installSecondCell() = installCell(index2, expected2)
        private fun installCell(cellIndex: Int, expected: E): Boolean {
            while (true) {
                when(val value = array[cellIndex]) {
                    this -> {
                        return true
                    }
                    is AtomicArrayWithCAS2AndImplementedDCSS<*>.CAS2Descriptor -> helpDescriptor(value)
                    expected -> {
                        if (dcss(cellIndex, expected, this, status, UNDECIDED)) {
                            return true
                        }
                    }
                    else -> {
                        return false
                    }
                }
            }
        }

        private fun helpDescriptor(
            foreignDescriptor: AtomicArrayWithCAS2AndImplementedDCSS<*>.CAS2Descriptor,
        ) {
            when (foreignDescriptor.status.get()) {
                SUCCESS -> foreignDescriptor.settleUpdate()
                UNDECIDED -> foreignDescriptor.apply()
                FAILED -> foreignDescriptor.restoreState()
                else -> {}
            }
        }

        private fun updateStatus(installed: Boolean) =
            status.compareAndSet(UNDECIDED, if (installed) SUCCESS else FAILED)

        private fun updateCells() {
            when (status.get()) {
                SUCCESS -> settleUpdate()
                FAILED -> restoreState()
                else -> {}
            }
        }

        private fun settleUpdate() {
            array.compareAndSet(index1, this, update1)
            array.compareAndSet(index2, this, update2)
        }

        private fun restoreState() {
            array.compareAndSet(index1, this, expected1)
            array.compareAndSet(index2, this, expected2)
        }

        fun getDescriptorCell(
            cellIndex: Int
        ) = if (status.get() == SUCCESS) {
            getUpdateCell(cellIndex)
        } else {
            getExpectedCell(cellIndex)
        }

        private fun getUpdateCell(cellIndex: Int) = if (cellIndex == index1) {
            update1
        } else {
            update2
        }

        private fun getExpectedCell(cellIndex: Int) = if (cellIndex == index1) {
            expected1
        } else { expected2 }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }

    // This DCSS implementation ensures that the status is `UNDECIDED` when installing the descriptor.
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