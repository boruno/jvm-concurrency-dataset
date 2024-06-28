@file:Suppress("DuplicatedCode")

package day3

import day3.AtomicArrayWithCAS2.Status.*
import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class AtomicArrayWithCAS2<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E {
        // TODO: the cell can store a descriptor
        val currentValue = array[index].value
        return if (currentValue is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
            val isFirstValue = index == currentValue.index1
            when (currentValue.status.value) {
                UNDECIDED, FAILED -> if (isFirstValue) currentValue.expected1 else currentValue.expected2
                SUCCESS -> if (isFirstValue) currentValue.update1 else currentValue.update2
            }
        } else {
            currentValue
        } as E
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        // TODO: the cell can store a descriptor
        return array[index].compareAndSet(expected, update)
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: Store a CAS2 descriptor in array[index1].
        val descriptor = CAS2Descriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2, update2 = update2
        )
        descriptor.apply()
        return descriptor.status.value === SUCCESS
    }

    private inner class CAS2Descriptor(
        index1: Int,
        expected1: E,
        update1: E,
        index2: Int,
        expected2: E,
        update2: E
    ) {
        val index1: Int
        val expected1: E
        val update1: E
        val index2: Int
        val expected2: E
        val update2: E

        init {
            // Sort
            if (index1 < index2) {
                this.index1 = index1
                this.index2 = index2
                this.expected1 = expected1
                this.expected2 = expected2
                this.update1 = update1
                this.update2 = update2
            } else {
                this.index1 = index2
                this.index2 = index1
                this.expected1 = expected2
                this.expected2 = expected1
                this.update1 = update2
                this.update2 = update1
            }
        }

        val status = atomic(UNDECIDED)

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            // TODO: In this task, only one thread can call cas2(..),
            // TODO: so cas2(..) calls cannot be executed concurrently.
            val success = installDescriptor()
            updateLogicalState(success)
            updatePhysically()
        }

        private fun installDescriptor(): Boolean {
            val success = installDescriptorToCell(index1, expected1)
            return success && installDescriptorToCell(index2, expected2)
        }

        private fun installDescriptorToCell(index: Int, expected: E): Boolean {
            while (status.value === UNDECIDED) {
                when (val currentValue = array[index].value) {
                    this -> return true
                    is AtomicArrayWithCAS2<*>.CAS2Descriptor -> {
                        // Help another CAS2 to finish its work
                        currentValue.apply()
                        continue
                    }
                    expected -> {
                        // TODO: Use dcss here
                        if (array[index].compareAndSet(expected, this)) {
                            return true
                        }
                    }
                    else -> return false
                }
            }
            return false
        }

        private fun updateLogicalState(success: Boolean) {
            val newStatus = if (success) SUCCESS else FAILED
            status.compareAndSet(UNDECIDED, newStatus)
        }

        private fun updatePhysically() {
            when (status.value) {
                SUCCESS -> {
                    array[index1].compareAndSet(this, update1)
                    array[index2].compareAndSet(this, update2)
                }
                FAILED -> {
                    array[index1].compareAndSet(this, expected1)
                    array[index2].compareAndSet(this, expected2)
                }
                UNDECIDED -> error("Unexpected")
            }
        }
    }

    private enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}