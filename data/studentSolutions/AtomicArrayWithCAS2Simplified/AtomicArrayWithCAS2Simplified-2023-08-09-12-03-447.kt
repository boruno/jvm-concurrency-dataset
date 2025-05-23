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
        // TODO: the cell can store CAS2Descriptor
        val state = array[index]
        if (state is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            state as AtomicArrayWithCAS2Simplified<E>.CAS2Descriptor
            return state.getValue(index)
        }
        return state as E

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

        fun getValue(index: Int): E {
            fun wrongIndex(): Nothing =
                error("Accessing wrong index $index. Descriptor works with indices $index1 and $index2")

            return when (status.get()!!) {
                UNDECIDED, FAILED -> if (index1 == index) expected1 else if (index2 == index) expected2 else wrongIndex()
                SUCCESS -> if (index1 == index) update1 else if (index2 == index) update2 else wrongIndex()
            }
        }

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            // TODO: In this task, only one thread can call cas2(..),
            // TODO: so cas2(..) calls cannot be executed concurrently.
            val success = installDescriptor()

            updateTheStatus(if (success) SUCCESS else FAILED)
            updateTheCells()
        }

        private fun installDescriptor(): Boolean {
            return array.compareAndSet(index1, expected1, this)
                    && array.compareAndSet(index2, expected2, this)
        }

        private fun updateTheStatus(newStatus: Status) {
            status.compareAndSet(UNDECIDED, newStatus)
        }

        private fun updateTheCells() {
            if (status.get() == SUCCESS) {
                array.compareAndSet(index1, this, update1)
                array.compareAndSet(index2, this, update2)
            } else {
                array.compareAndSet(index1, this, expected1)
                array.compareAndSet(index2, this, expected2)
            }
            checkForeignDescriptors()
        }

        private fun checkForeignDescriptors() {
            if (status.get() == SUCCESS) return
            val values = listOf(array[index1], array[index2])
            for (value in values) {
                if (value is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
                    value.apply()
                }
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}