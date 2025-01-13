//package day3

import AtomicArrayWithCAS2SingleWriter.Status.*
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

// This implementation never stores `null` values.
class AtomicArrayWithCAS2SingleWriter<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        val value = array[index].value
        return if (value is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            if (value.status.value === SUCCESS) {
                if (value.index1 == index) {
                    value.update1
                } else {
                    value.update2
                }
            } else {
                if (value.index1 == index) {
                    value.expected1
                } else {
                    value.expected2
                }
            }
        } else {
            value
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
        return descriptor.status.value === SUCCESS
    }

    private inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            // TODO: In this task, only one thread can call cas2(..),
            // TODO: so cas2(..) calls cannot be executed concurrently.
            install()
            updateStatus()
            updateCells()
        }

        private fun install() {
            array[index1].compareAndSet(expected1, this)
            array[index2].compareAndSet(expected2, this)
        }

        private fun updateStatus() {
            val value = array[index2].value
            val newStatus = if (value === this) SUCCESS else FAILED
            status.value = newStatus
        }

        private fun updateCells() {
            when (status.value) {
                UNDECIDED -> throw IllegalStateException()
                SUCCESS -> {
                    array[index1].compareAndSet(this, update1)
                    array[index2].compareAndSet(this, update2)
                }

                FAILED -> {
                    array[index1].compareAndSet(this, expected1)
                    array[index2].compareAndSet(this, expected2)
                }
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}