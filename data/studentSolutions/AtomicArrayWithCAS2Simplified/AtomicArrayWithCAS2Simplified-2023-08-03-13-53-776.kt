//package day3

import AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.*
import java.util.NoSuchElementException
import kotlin.math.acos


// This implementation never stores `null` values.
class AtomicArrayWithCAS2Simplified<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E {
        return when(val value = array[index].value) {
            is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> value[index] as E
            else -> value as E
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = newDescriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2, update2 = update2
        )
        descriptor.apply()
        return descriptor.status.value === SUCCESS
    }

    fun newDescriptor(
        index1: Int,
        expected1: E,
        update1: E,
        index2: Int,
        expected2: E,
        update2: E
    ): CAS2Descriptor =
        if (index1 > index2)
            CAS2Descriptor(index2, expected2, update2, index1, expected1, update1)
        else
            CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)

    inner class CAS2Descriptor(
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E,
        private val update2: E
    ) {
        val status = atomic(UNDECIDED)

        operator fun get(i: Int): E = when (status.value) {
            UNDECIDED, FAILED -> when (i) {
                index1 -> expected1
                index2 -> expected2
                else -> throw NoSuchElementException()
            }
            SUCCESS -> when (i) {
                index1 -> update1
                index2 -> update2
                else -> throw NoSuchElementException()
            }
        }

        fun apply() {
            while(true) {
                installDescriptor()
                if (!updateStatus())
                    continue
                updateCells()
                return
            }
        }

        private fun installDescriptor(): Boolean =
            (array[index1].compareAndSet(expected1, this) || array[index1].value == this)
                    && (array[index2].compareAndSet(expected2, this) || array[index2].value == this)

        // returns false when I need to help another descriptor
        private fun updateStatus(): Boolean {
            val success = installationSuccess() ?: return false
            val newStatus = if (success) SUCCESS else FAILED
            status.compareAndSet(UNDECIDED, newStatus)
            return true
        }

        // returns true/false when status is known, null when I need to help another thread
        private fun installationSuccess(): Boolean? {
            val actual1 = array[index1].value as? AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor
            val actual2 = array[index2].value as? AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor
            return if (actual1 == this && actual2 == this)
                true
            else if (actual1 != this && actual1 != null && actual1.isUndecided()) {
                actual1.apply()
                null
            }
            else if (actual2 != this && actual2 != null && actual2.isUndecided()) {
                actual2.apply()
                null
            }
            else false

        }

        private fun updateCells() {
            when (status.value) {
                SUCCESS -> {
                    array[index1].compareAndSet(this, update1)
                    array[index2].compareAndSet(this, update2)
                }
                FAILED, UNDECIDED -> {
                    array[index1].compareAndSet(this, expected1)
                    array[index2].compareAndSet(this, expected2)
                }
            }
        }

        fun isUndecided() = status.value == UNDECIDED
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}