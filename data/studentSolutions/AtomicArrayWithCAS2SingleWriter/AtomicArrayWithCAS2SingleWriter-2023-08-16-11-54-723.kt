@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import AtomicArrayWithCAS2SingleWriter.Status.*
import java.util.concurrent.atomic.*

// This implementation never stores `null` values.
class AtomicArrayWithCAS2SingleWriter<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    fun get(index: Int): E = array[index].run {
        if (this is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            this as AtomicArrayWithCAS2SingleWriter<E>.CAS2Descriptor
            // Read the logical value.
            if (status.get() === SUCCESS) {
                if (index == index1) update1 else update2
            } else {
                if (index == index1) expected1 else expected1
            }
        } else {
            // The cell stores a value, return it.
            this as E
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E, index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = CAS2Descriptor(
            index1 = index1, expected1 = expected1, update1 = update1, index2 = index2, expected2 = expected2, update2 = update2
        )
        descriptor.apply()
        return descriptor.status.get() === SUCCESS
    }

    private inner class CAS2Descriptor(
        val index1: Int, val expected1: E, val update1: E, val index2: Int, val expected2: E, val update2: E
    ) {
        val status = AtomicReference(UNDECIDED)

        fun apply() {
            val success = installDescriptor()
            applyLogically(success)
            applyPhysically()
        }

        private fun installDescriptor(): Boolean {
            if (!installDescriptor(index1, expected1)) return false
            return installDescriptor(index2, expected2)
        }

        private fun installDescriptor(index: Int, expected: E): Boolean =
            if (array[index] === expected) {
                array.set(index, this)
                true
            } else { // unexpected value
                false
            }

        private fun applyLogically(success: Boolean) {
            status.compareAndSet(
                UNDECIDED, if (success) SUCCESS else FAILED
            )
        }

        private fun applyPhysically() {
            if (status.get() == SUCCESS) {
                array.compareAndSet(index1, this, update1)
                array.compareAndSet(index2, this, update2)
            } else {
                array.compareAndSet(index1, this, expected1)
                array.compareAndSet(index2, this, expected2)
            }
        }
    }

    private enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}