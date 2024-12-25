@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import day3.AtomicArrayWithCAS2SingleWriter.Status.*
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

    fun get(index: Int): E {
        val value = array[index]
        return if (value is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            when(value.status.get()) {
                UNDECIDED, FAILED -> value.getExpectedForIndex(index) as E
                SUCCESS -> value.getUpdateForIndex(index) as E
                else -> throw IllegalStateException()
            }
        } else {
            array[index] as E
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
        return descriptor.status.get() === SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int, val expected1: E, val update1: E,
        val index2: Int, val expected2: E, val update2: E
    ) {
        val status = AtomicReference(UNDECIDED)

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            // TODO: In this task, only one thread can call cas2(..),
            // TODO: so cas2(..) calls cannot be executed concurrently.
            installDescriptor()
            updateStatus()
            updateCells()
        }

        private fun installDescriptor() {

            if (array.compareAndSet(index1, expected1, this)) {
                if (array.compareAndSet(index2, expected2, this)) {
                    status.compareAndSet(UNDECIDED, SUCCESS)
                } else {
                    status.compareAndSet(UNDECIDED, FAILED)
                }
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }
        }

        private fun updateStatus() {
            //if (array.compareAndSet(index1, expected1, ))
        }

        private fun updateCells() {
            when (status.get()) {
                FAILED -> array.compareAndSet(index1, this, expected1) && array.compareAndSet(index2, this, expected2)
                SUCCESS -> array.compareAndSet(index1, this, update1) && array.compareAndSet(index2, this, update2)
                else -> throw IllegalStateException("State is $UNDECIDED")
            }
        }

        fun getExpectedForIndex(index: Int): E  {
            return when (index) {
                index1 -> expected1
                index2 -> expected2
                else -> throw IllegalArgumentException("Current CAS2 operation does not know $index")
            }
        }

        fun getUpdateForIndex(index: Int): E  {
            return when (index) {
                index1 -> update1
                index2 -> update2
                else -> throw IllegalArgumentException("Current CAS2 operation does not know $index")
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}