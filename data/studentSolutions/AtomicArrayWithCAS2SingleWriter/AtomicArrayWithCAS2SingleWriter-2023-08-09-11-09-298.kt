@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import AtomicArrayWithCAS2SingleWriter.Status.SUCCESS
import AtomicArrayWithCAS2SingleWriter.Status.UNDECIDED
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray

// This implementation never stores `null` values.
class AtomicArrayWithCAS2SingleWriter<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    fun get(index: Int): E {
        val result = array[index]
        if (result is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            return if (result.status.get() == SUCCESS)
                result.getExpectedByIndex(index) as E
            else
                result.getUpdateByIndex(index) as E
        }
        return result as E
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
        return descriptor.status.get() === SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int, val expected1: E, val update1: E,
        val index2: Int, val expected2: E, val update2: E
    ) {
        val status = AtomicReference(UNDECIDED)

        fun getExpectedByIndex(index: Int) = if (index == index1) expected1 else expected2
        fun getUpdateByIndex(index: Int) = if (index == index1) update1 else update2

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            // TODO: In this task, only one thread can call cas2(..),
            // TODO: so cas2(..) calls cannot be executed concurrently.

            val descriptorsInstalled = installDescriptors()
            updateStatus(descriptorsInstalled)
            updatePhysically()
        }

        private fun installDescriptors(): Boolean {
            return array.compareAndSet(index1, expected1, this) &&
                    array.compareAndSet(index2, expected2, this)
        }

        private fun updateStatus(descriptorsInstalled: Boolean) {
            if (descriptorsInstalled) status.set(SUCCESS) else status.set(Status.FAILED)
        }

        private fun updatePhysically() {
            if (status.get() == SUCCESS) {
                array[index1] = update1
                array[index2] = update2
            } else {
                array[index1] = expected1
                array[index2] = expected2
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}