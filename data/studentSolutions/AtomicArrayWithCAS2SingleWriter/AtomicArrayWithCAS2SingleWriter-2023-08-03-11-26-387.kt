//package day3

import day3.AtomicArrayWithCAS2SingleWriter.Status.*
import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class AtomicArrayWithCAS2SingleWriter<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E {
        // TODO: the cell can store CAS2Descriptor
        val value = array[index].value
        return when {
            value !is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor -> value as E
            else -> value.read(index) as E
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
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E,
        private val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun read(index: Int): E = when (status.value) {
            Status.SUCCESS -> if (index == index1) update1 else update2
            else -> if (index == index1) expected1 else expected2
        }

        fun apply() {
            val isSuccess = installDescriptor(index1, expected1) && installDescriptor(index2, expected2)
            updateStatus(isSuccess)
            updateCell(index1, update1)
            updateCell(index2, update2)
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            // TODO: In this task, only one thread can call cas2(..),
            // TODO: so cas2(..) calls cannot be executed concurrently.
        }

        fun installDescriptor(index: Int, expected: E): Boolean {
            val sell = array[index]
            return sell.compareAndSet(expected, this)
        }

        fun updateStatus(isSuccess: Boolean) {
            val update = if (isSuccess) SUCCESS else FAILED
            status.compareAndSet(UNDECIDED, update)
        }

        fun updateCell(index: Int, updatedValue: E) {
            val sell = array[index]
            sell.compareAndSet(this, updatedValue)
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}