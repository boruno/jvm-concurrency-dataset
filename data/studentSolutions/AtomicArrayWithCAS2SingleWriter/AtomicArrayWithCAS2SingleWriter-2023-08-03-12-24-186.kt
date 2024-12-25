@file:Suppress("DuplicatedCode", "IfThenToSafeAccess")

//package day3

import day3.AtomicArrayWithCAS2SingleWriter.Status.*
import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class AtomicArrayWithCAS2SingleWriter<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)
    private val descriptor = atomic<CAS2Descriptor?>(null)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E {
        while (true) {
            val currentDescriptor = descriptor.value
            val currentDescriptorStatus = if (currentDescriptor != null ) currentDescriptor.status.value else null

            if (currentDescriptor != null && currentDescriptorStatus == SUCCESS) {
                if (currentDescriptor.index1 == index)
                    return currentDescriptor.expected1
                else if (currentDescriptor.index2 == index)
                    return currentDescriptor.expected2
            }

            @Suppress("UNCHECKED_CAST")
            val result = array[index].value as E

            val newDescriptor = descriptor.value
            if (newDescriptor != null && newDescriptor != currentDescriptor)
                continue

            return result
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
        return descriptor.status.value === SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E,
        private val update1: E,
        val index2: Int,
        val expected2: E,
        private val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            descriptor.value = this // Install it.
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
            descriptor.value = null
        }

        private fun handleUndecided() {
            if (array[index1].value != expected1 || array[index2].value != expected2) {
                status.value = FAILED
                return
            }

            status.value = SUCCESS
            array[index1].value = update1
            array[index2].value = update2

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