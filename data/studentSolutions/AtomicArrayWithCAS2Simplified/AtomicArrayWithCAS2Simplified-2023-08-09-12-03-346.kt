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

    fun get(index: Int): E {
        val value = array[index]
        if (value !is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            return value as E
        }
        return when (value.status.get()!!) {
            AtomicArrayWithCAS2SingleWriter.Status.SUCCESS -> if (index == value.index1) value.update1 else value.update2
            AtomicArrayWithCAS2SingleWriter.Status.FAILED, AtomicArrayWithCAS2SingleWriter.Status.UNDECIDED -> if (index == value.index1) value.expected1 else value.expected2
        } as E
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
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E,
        private val update2: E
    ) {
        val status = AtomicReference(UNDECIDED)

        tailrec fun apply() {
            if (help(index1) || help(index2)) return apply()
            if (!updateLogically()) return status.set(FAILED)
            if (help(index1) || help(index2)) return apply()
            updateStatus()
            if (help(index1) || help(index2)) return apply()
            updatePhysically()
        }

        private fun help(i: Int): Boolean {
            val descriptor = array[i] as? AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor ?: return false
            descriptor.apply()
            return true
        }

        private fun updatePhysically() {
            array.compareAndSet(index1, this, update1)
            array.compareAndSet(index2, this, update2)
        }

        private fun updateStatus() {
            status.set(SUCCESS)
        }

        private fun updateLogically(): Boolean {
            if (!array.compareAndSet(index1, expected1, this)) return false
            if (array.compareAndSet(index2, expected2, this)) return true
            array.compareAndSet(index1, this, expected1)
            return false
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}