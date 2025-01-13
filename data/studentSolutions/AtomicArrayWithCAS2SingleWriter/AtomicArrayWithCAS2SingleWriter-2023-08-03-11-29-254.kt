@file:Suppress("DuplicatedCode")

//package day3

import AtomicArrayWithCAS2SingleWriter.Status.*
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
        val currentDescriptor = descriptor.value

        var valueFromDescriptor: E? = null
        if (currentDescriptor != null && currentDescriptor.status.value != SUCCESS) {
            if (currentDescriptor.index1 == index)
                valueFromDescriptor = currentDescriptor.expected1
            else if (currentDescriptor.index2 == index)
                valueFromDescriptor = currentDescriptor.expected2

            currentDescriptor.proceed()
        }

        return valueFromDescriptor ?: (array[index].value as E)
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
            val previous1 = array[index1].getAndSet(update1)
            if (previous1 != expected1 && previous1 != update1) {
                status.value = FAILED
                return proceedFailed()
            }

            val previous2 = array[index2].getAndSet(update2)
            if (previous2 != expected2 && previous2 != update2) {
                status.value = FAILED
                return proceedFailed()
            }

            status.value = SUCCESS
            return uninstall()
        }

        private fun proceedFailed() {
            array[index1].value = expected1
            array[index2].value = expected2
            uninstall()
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}