@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import java.util.concurrent.atomic.*

// This implementation never stores `null` values.
class AtomicArrayWithCAS2<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    fun get(index: Int): E? {
        while (true) {
            return when (val item: Any? = array[index]) {
                // the cell can store CAS2Descriptor
                is AtomicArrayWithCAS2<*>.CAS2Descriptor -> {
                    item.getDescriptorCell(index) as? E
                }

                is AtomicArrayWithCAS2<*>.DcssDescriptor -> {
                    item.getDescriptorValue(index) as? E
                }

                else -> {
                    item as? E
                }
            }
        }
    }

    fun cas2(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?, update2: E?
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
        return descriptor.status.get() === Status.SUCCESS
    }

    inner class CAS2Descriptor(
        private val index1: Int,
        private val expected1: E?,
        private val update1: E?,
        private val index2: Int,
        private val expected2: E?,
        private val update2: E?
    ) {
        val status = AtomicReference(Status.UNDECIDED)

        fun apply() {
            // Install the descriptor;
            val installed = install()
            // update the status
            updateStatus(installed)
            // and update the cells
            updateCells()
        }

        fun getDescriptorCell(
            cellIndex: Int
        ) = if (status.get() == Status.SUCCESS) {
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

        private fun install(): Boolean = if (index1 < index2) {
            installFirstCell() && installSecondCell()
        } else {
            installSecondCell() && installFirstCell()
        }

        private fun updateStatus(installed: Boolean) =
            status.compareAndSet(Status.UNDECIDED, if (installed) Status.SUCCESS else Status.FAILED)

        private fun updateCells() {
            when (status.get()) {
                Status.SUCCESS -> settleUpdate()
                Status.FAILED -> restoreState()
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

        private fun installFirstCell() = installCell(index1, expected1)
        private fun installSecondCell() = installCell(index2, expected2)
        private fun installCell(cellIndex: Int, expected: E?): Boolean {
            while (true) {
                if (status.get() != Status.UNDECIDED) return false

                when(val value = array[cellIndex]) {
                    this -> {
                        return true
                    }
                    is AtomicArrayWithCAS2<*>.CAS2Descriptor -> helpDescriptor(value)
                    is AtomicArrayWithCAS2<*>.DcssDescriptor -> {
                        value.updateCells(value.statusExpected())
                    }
//                        if (value.getNextDescriptorValue(cellIndex) == expected) { // todo: could change to next value
//                        if (dcss(cellIndex, expected, this, status, Status.UNDECIDED)) {
//                            return true
//                        }
//                    } else {
//                        return false
//                    }
                    expected -> {
                        if (dcss(cellIndex, expected, this, status, Status.UNDECIDED)) {
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
            foreignDescriptor: AtomicArrayWithCAS2<*>.CAS2Descriptor,
        ) {
            when (foreignDescriptor.status.get()) {
                Status.SUCCESS -> foreignDescriptor.settleUpdate()
                Status.UNDECIDED -> foreignDescriptor.apply()
                Status.FAILED -> foreignDescriptor.restoreState()
                else -> {}
            }
        }
//        fun getDescriptorCell(
//            cellIndex: Int
//        ) = if (status.get() == Status.SUCCESS) {
//            getUpdateCell(cellIndex)
//        } else {
//            getExpectedCell(cellIndex)
//        }
    }

    private fun dcss(
        index: Int,
        expectedCellState: Any?,
        updateCellState: Any?,
        statusReference: AtomicReference<*>,
        expectedStatus: Any?
    ): Boolean {
        val descriptor = DcssDescriptor(index, expectedCellState, updateCellState, statusReference, expectedStatus)
        return descriptor.apply()
    }

    inner class DcssDescriptor(
        private val index: Int,
        private val expectedCellState: Any?,
        private val updateCellState: Any?,
        private val statusReference: AtomicReference<*>,
        private val expectedStatus: Any?
    ) {
        fun apply(): Boolean {
            // Install the descriptor;
            val installed = install()
            // and update the cells
            return updateCells(installed)
        }

        fun getNextDescriptorValue(index: Int): E? = when (updateCellState) {
            is AtomicArrayWithCAS2<*>.DcssDescriptor -> updateCellState.getDescriptorValue(index)
            is AtomicArrayWithCAS2<*>.CAS2Descriptor -> updateCellState.getDescriptorCell(index)
            else -> updateCellState
        } as? E

        fun getDescriptorValue(index: Int): E? = when (updateCellState) {
            is AtomicArrayWithCAS2<*>.DcssDescriptor -> updateCellState.getDescriptorValue(index)
            is AtomicArrayWithCAS2<*>.CAS2Descriptor -> updateCellState.getDescriptorCell(index)
            else -> if (statusReference.get() == expectedStatus) {
                updateCellState
            } else {
                expectedCellState
            }
        } as? E

        private fun install(): Boolean =
//            when (val arrayItem = array[index]) {
//                is AtomicArrayWithCAS2<*>.DcssDescriptor -> if (getNextDescriptorValue(index) == expectedCellState) { // todo: could also change
//                    array.compareAndSet(index, arrayItem, this)
//                } else {
//                    false
//                }
//                expectedCellState -> array.compareAndSet(index, expectedCellState, this)
//                else -> false
//            } && statusReference.get() == expectedStatus
            array.compareAndSet(index, expectedCellState, this) && statusExpected()

        fun statusExpected() = statusReference.get() == expectedStatus

        fun updateCells(installed: Boolean): Boolean =
            if (installed) {
                settleUpdate()
            } else {
                restoreState()
                false
            }

        private fun settleUpdate() = array.compareAndSet(index, this, updateCellState)

        private fun restoreState() {
            array.compareAndSet(index, this, expectedCellState)
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}