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

    fun get(index: Int): E = when (val item = array[index]) {
        // the cell can store CAS2Descriptor
        is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> item.getDesciptorCell(index) as E
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
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E,
        private val update2: E
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

        private fun install(): Boolean = installFirstCell() && installSecondCell()
//            if (index1 < index2) {
//            installFirstCell() && installSecondCell()
//        } else {
//            installSecondCell() && installFirstCell()
//        }

        private fun installFirstCell() = installCell(index1, expected1)
        private fun installSecondCell() = installCell(index2, expected2)
        private fun installCell(cellIndex: Int, expected: E): Boolean {
            while (true) {
                when(val value = array[cellIndex]) {
                    is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> if (value == this) {
                        return true
                    } else {
                        helpDescriptor(cellIndex, value)
                    }
                    else -> {
                        return array.compareAndSet(cellIndex, expected, this)
                    }
                }
            }
        }

        private fun helpDescriptor(
            cellIndex: Int,
            foreignDescriptor: AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor,
        ) {
            when (foreignDescriptor.status.get()) {
                SUCCESS -> foreignDescriptor.settleUpdate()
                UNDECIDED -> helpUndecidedDescriptor(cellIndex, foreignDescriptor)
                FAILED -> foreignDescriptor.restoreState()
                else -> {}
            }
        }

        private fun helpUndecidedDescriptor(
            cellIndex: Int,
            foreignDescriptor: AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor
        ) {
            foreignDescriptor.apply()
//            val installed = installForeignCell(cellIndex, foreignDescriptor)
//            foreignDescriptor.updateStatus(installed)
//            foreignDescriptor.updateCells()
        }

        private fun installForeignCell(
            myIndex: Int,
            foreignDescriptor: AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor
        ): Boolean = if (foreignDescriptor.index1 == myIndex) { // find cell equal to my index and update another since this cell is already installed (descriptor is there)
            // install foreign index 2
            foreignDescriptor.installSecondCell()
        } else {
            // install foreign index 1
            foreignDescriptor.installFirstCell()
        }

        private fun updateStatus(installed: Boolean) = if (installed) {
            status.compareAndSet(UNDECIDED, SUCCESS)
        } else {
            status.compareAndSet(UNDECIDED, FAILED)
        }

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

        fun getDesciptorCell(
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
}