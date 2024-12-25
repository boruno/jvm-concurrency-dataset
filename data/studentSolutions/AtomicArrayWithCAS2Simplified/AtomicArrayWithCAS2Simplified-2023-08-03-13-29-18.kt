@file:Suppress("DuplicatedCode", "IfThenToSafeAccess")

//package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class AtomicArrayWithCAS2Simplified<E : Any>(size: Int, initialValue: E) {
    private val array = Array(size) { Cell(initialValue) }

    fun get(index: Int): E {
        val cell = array[index]

        val currentDescriptor = cell.descriptor.value
        val currentDescriptorStatus = if (currentDescriptor != null) currentDescriptor.status.value else null

        val cellValue = cell.value.value

        if (currentDescriptor != null && currentDescriptorStatus == SUCCESS) {
            if (currentDescriptor.firstIndex == index)
                return currentDescriptor.firstUpdate
            else if (currentDescriptor.secondIndex == index)
                return currentDescriptor.secondUpdate
        }

        return cellValue
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
        return descriptor.status.value === SUCCESS
    }

    inner class Cell(initialValue: E) {
        val value = atomic(initialValue)
        val descriptor = atomic<CAS2Descriptor?>(null)
    }

    inner class CAS2Descriptor(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ) {
        val status = atomic(UNDECIDED)

        val firstIndex: Int
        private val firstCell: Cell
        private val firstExpected: E
        val firstUpdate: E

        val secondIndex: Int
        private val secondCell: Cell
        private val secondExpected: E
        val secondUpdate: E

        init {
            if (index1 < index2) {
                firstIndex = index1
                firstCell = array[index1]
                firstExpected = expected1
                firstUpdate = update1

                secondIndex = index2
                secondCell = array[index2]
                secondExpected = expected2
                secondUpdate = update2
            } else {
                firstIndex = index2
                firstCell = array[index2]
                firstExpected = expected2
                firstUpdate = update2

                secondIndex = index1
                secondCell = array[index1]
                secondExpected = expected1
                secondUpdate = update1
            }
        }

        fun apply() {
            while (true) {
                if (!firstCell.descriptor.compareAndSet(null, this)) {
                    val existingDescriptor = firstCell.descriptor.value
                    if (existingDescriptor == this)
                        break

                    existingDescriptor?.apply()
                    continue
                } else
                    break
            }

            while (true) {
                if (!secondCell.descriptor.compareAndSet(null, this)) {
                    val existingDescriptor = secondCell.descriptor.value
                    if (existingDescriptor == this)
                        break

                    existingDescriptor?.apply()
                    continue
                } else
                    break
            }

            proceed()
        }

        private fun proceed() {
            when (status.value) {
                SUCCESS -> handleSuccess()
                UNDECIDED -> handleUndecided()
                FAILED -> handleFailed()
            }
        }

        private fun handleSuccess() {
            firstCell.value.value = firstUpdate
            secondCell.value.value = secondUpdate
            uninstall()
        }

        private fun uninstall() {
            firstCell.descriptor.compareAndSet(this, null)
            secondCell.descriptor.compareAndSet(this, null)
        }

        private fun handleUndecided() {
            val hasExpectedValues =
                firstCell.value.value == firstExpected && secondCell.value.value == secondExpected

            when (status.value) {
                UNDECIDED -> {
                    if (hasExpectedValues) {
                        status.value = SUCCESS
                        handleSuccess()
                    }
                    else {
                        status.value = FAILED
                        handleFailed()
                    }
                }
                SUCCESS -> handleSuccess()
                FAILED -> handleFailed()
            }
        }

        private fun handleFailed() {
            uninstall()
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}