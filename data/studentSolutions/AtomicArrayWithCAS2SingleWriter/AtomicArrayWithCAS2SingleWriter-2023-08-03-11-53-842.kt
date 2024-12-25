@file:Suppress("DuplicatedCode")

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

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        while (true) {
            val currentDescriptor = descriptor.value
            val currentDescriptorStatus = if (currentDescriptor != null ) currentDescriptor.status.value else null

            if (currentDescriptor != null && (currentDescriptorStatus == UNDECIDED || currentDescriptorStatus == FAILED)) {
                if (currentDescriptor.index1 == index)
                    return currentDescriptor.expected1
                else if (currentDescriptor.index2 == index)
                    return currentDescriptor.expected2
            }

            val result = array[index].value as E

            val newDescriptor = descriptor.value
            if (newDescriptor != null && currentDescriptor != null && newDescriptor != currentDescriptor)
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
            while (true) {
                val actual1 = array[index1].value
                val actual2 = array[index2].value

                if (actual1 != array[index1].value)
                    continue

                if (actual1 != expected1 || actual2 != expected2) {
                    status.value = FAILED
                    return
                } else
                    break
            }

            descriptor.value = this // Install it.
            proceed()
        }

        fun proceed() {
            when (status.value) {
                SUCCESS -> uninstall()
                UNDECIDED -> proceedUndecided()
                FAILED -> proceedFailed()
            }
        }

        private fun uninstall() {
            descriptor.compareAndSet(this, null)
        }

        private fun proceedUndecided() {
            if (!array[index1].compareAndSet(expected1, update1)) {
                status.value = FAILED
                return proceedFailed()
            }

            if (!array[index2].compareAndSet(expected2, update2)) {
                status.value = FAILED
                return proceedFailed()
            }

            status.value = SUCCESS
            return uninstall()
        }

        private fun proceedFailed() {
            array[index1].compareAndSet(update1, expected1)
            array[index2].compareAndSet(update2, expected2)
            uninstall()
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}