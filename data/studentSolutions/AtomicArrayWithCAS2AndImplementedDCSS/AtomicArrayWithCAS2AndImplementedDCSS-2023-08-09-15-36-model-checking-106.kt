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
        // TODO: Copy the implementation from `AtomicArrayWithCAS2Simplified`
        val cell = array[index]
        return if (cell is AtomicArrayWithCAS2AndImplementedDCSS<*>.CAS2Descriptor) {
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
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
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
            while (true) {
                if (status.get() != UNDECIDED) return false
                when (val cell = array[index]) {
                    this -> return true

                    is AtomicArrayWithCAS2AndImplementedDCSS<*>.CAS2Descriptor -> {
                        cell.apply()
                    }

                    expected -> {
                        return if (dcss(index, expected, this, status, UNDECIDED)) true else continue
                    }

                    else -> return false
                }
            }
        }

        private fun updateStatus(success: Boolean) {
            status.compareAndSet(UNDECIDED, if (success) SUCCESS else FAILED)
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

    // TODO: Please use this DCSS implementation to ensure that
    // TODO: the status is `UNDECIDED` when installing the descriptor.
    fun dcss(
        index: Int,
        expectedCellState: Any?,
        updateCellState: Any?,
        statusReference: AtomicReference<*>,
        expectedStatus: Any?
    ): Boolean {
        val descriptor = DCSSDescriptor(
            index,
            expectedCellState,
            updateCellState,
            statusReference as AtomicReference<Any?>,
            expectedStatus
        )
        descriptor.apply()
        return descriptor.dcssStatus.get() === SUCCESS
//        if (array[index] == expectedCellState && statusReference.get() == expectedStatus) {
//            array[index] = updateCellState
//            return true
//        } else {
//            return false
//        }
    }

    inner class DCSSDescriptor(
        val index: Int,
        val expected: Any?,
        val update: Any?,
        val statusReference: AtomicReference<Any?>,
        val expectedStatus: Any?,

        ) {
        val dcssStatus = AtomicReference(UNDECIDED)

        fun apply() {
            val success = install()
            updateStatus(success)
            updatePhysically()
        }

        fun install(): Boolean {
            while (true) {
                if (dcssStatus.get() != UNDECIDED) return false
                val cell = array[index]
                when (cell) {
                    this -> return true
                    is AtomicArrayWithCAS2AndImplementedDCSS<*>.DCSSDescriptor -> {
                        cell.apply()
                    }

                    expected -> {
                        return if (statusReference.compareAndSet(expectedStatus, SUCCESS)) true else continue
                    }

                    else -> return false
                }
            }
        }

        private fun updateStatus(success: Boolean) {
            dcssStatus.compareAndSet(UNDECIDED, if (success) SUCCESS else FAILED)
        }

        private fun updatePhysically() {
            if (dcssStatus.get() == SUCCESS)
                array.compareAndSet(index, this, update)
        }
    }
}