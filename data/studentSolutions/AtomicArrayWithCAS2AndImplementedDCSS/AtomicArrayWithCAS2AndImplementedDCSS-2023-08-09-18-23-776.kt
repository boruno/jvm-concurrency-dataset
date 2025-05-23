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

    fun get(index: Int): E {
        val value = array[index]
        return if (value is AtomicArrayWithCAS2AndImplementedDCSS<*>.CAS2Descriptor) {
            value.getValueForIndex(index) as E
        } else {
            value as E
        }
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
            // TODO: In this task, only one thread can call cas2(..)
            val installResult = installDescriptor()
            updateStatus(installResult)
            updateCells()
        }

        private fun installDescriptor(): Boolean {
            val indexes = listOf(index1, index2).sortedDescending()

            return if (installCellDescriptor(indexes[0])) {
                installCellDescriptor(indexes[1])
            } else {
                false
            }
        }

        private fun installCellDescriptor(i: Int): Boolean {
            if (status.get() != UNDECIDED) {
                return false
            }

            val expectedValue = getExpectedForIndex(i)
            while (true) {
                val cellValue = array[i]
                when (cellValue) {
                    expectedValue -> {
                        if (dcss(i, expectedValue, this, status, UNDECIDED)) {
                            return true
                        } else {
                            continue
                        }
                    }

                    this -> {
                        return true
                    }

                    is AtomicArrayWithCAS2AndImplementedDCSS<*>.CAS2Descriptor -> {
                        cellValue.apply()
                        continue
                    }

                    else -> {
                        return false
                    }
                }
            }
        }

        private fun updateStatus(isInstallSuccessful: Boolean) {
            if (isInstallSuccessful) {
                status.compareAndSet(UNDECIDED, SUCCESS)
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }
        }

        fun updateCells() {
            if (status.get() == SUCCESS) {
                array.compareAndSet(index1, this, update1)
                array.compareAndSet(index2, this, update2)
            } else {
                array.compareAndSet(index1, this, expected1)
                array.compareAndSet(index2, this, expected2)
            }
        }

        fun getValueForIndex(i: Int): E {
            return if (status.get() == SUCCESS) {
                getUpdatedForIndex(i)
            } else {
                getExpectedForIndex(i)
            }
        }

        fun getUpdatedForIndex(i: Int): E {
            return if (i == index1) {
                return update1
            } else {
                update2
            }
        }

        fun getExpectedForIndex(i: Int): E {
            return if (i == index1) {
                return expected1
            } else {
                expected2
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
    ): Boolean =
        if (array[index] == expectedCellState && statusReference.get() == expectedStatus) {
            array[index] = updateCellState
            true
        } else {
            false
        }
}