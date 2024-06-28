package day3

import day3.AtomicArrayWithDCSS.Status.*
import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class AtomicArrayWithDCSS<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E? {
        // TODO: the cell can store a descriptor
        val currentValue = array[index].value
        return if (currentValue is AtomicArrayWithDCSS<*>.DCASDescriptor) {
            when (currentValue.status.value) {
                UNDECIDED, FAILED -> currentValue.expected1
                SUCCESS -> currentValue.update1
            }
        } else {
            currentValue
        } as E
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        val cell = array[index]
        while (true) {
            val value = cell.value
            if (value is AtomicArrayWithDCSS<*>.DCASDescriptor) {
                value.apply()
            } else {
                val result = cell.compareAndSet(expected, update)
                return result
            }
        }
    }

    fun dcss(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO This implementation is not linearizable!
        // TODO Store a DCSS descriptor in array[index1].

        val descriptor = DCASDescriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2
        )
        descriptor.apply()
        return descriptor.status.value === SUCCESS
    }

    private inner class DCASDescriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            // TODO: In this task, only one thread can call cas2(..),
            // TODO: so cas2(..) calls cannot be executed concurrently.
            val success = installDescriptor()
            updateLogicalState(success && array[index2].value == expected2)
            updatePhysically()
        }

        private fun installDescriptor(): Boolean {
            return installDescriptorToCell(index1, expected1)
        }

        private fun installDescriptorToCell(index: Int, expected: E): Boolean {
            while (status.value === UNDECIDED) {
                when (val currentValue = array[index].value) {
                    this -> return true
                    is AtomicArrayWithDCSS<*>.DCASDescriptor -> {
                        // Help another CAS2 to finish its work
                        currentValue.apply()
                        continue
                    }
                    expected -> {
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
                }
                FAILED -> {
                    array[index1].compareAndSet(this, expected1)
                }
                UNDECIDED -> error("Unexpected")
            }
        }
    }

    private enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}