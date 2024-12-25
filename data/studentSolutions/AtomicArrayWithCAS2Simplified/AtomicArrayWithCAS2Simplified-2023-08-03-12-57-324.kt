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
            val currentDescriptorStatus = if (currentDescriptor != null ) currentDescriptor.status.value else null

            val cellValue = cell.value.value

            if (currentDescriptor != null && currentDescriptorStatus == SUCCESS) {
                if (currentDescriptor.index1 == index)
                    return currentDescriptor.update1
                else if (currentDescriptor.index2 == index)
                    return currentDescriptor.update2
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
        val index1: Int,
        private val expected1: E,
        val update1: E,
        val index2: Int,
        private val expected2: E,
        val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            array[index1].descriptor.value = this
            array[index2].descriptor.value = this
            proceed()
        }

        private fun proceed() {
            when (status.value) {
                SUCCESS -> uninstall()
                UNDECIDED -> handleUndecided()
                FAILED -> handleFailed()
            }
        }

        private fun uninstall() {
            array[index1].descriptor.value = null
            array[index2].descriptor.value = null
        }

        private fun handleUndecided() {
            if (array[index1].value.value != expected1 || array[index2].value.value != expected2) {
                status.value = FAILED
                return
            }

            status.value = SUCCESS
            array[index1].value.value = update1
            array[index2].value.value = update2

            return uninstall()
        }

        private fun handleFailed() {
            uninstall()
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}