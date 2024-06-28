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

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        // TODO: the cell can store a descriptor
        val currentValue = array[index].value

        return when (currentValue) {
            is AtomicArrayWithCAS2<*>.CAS2Descriptor -> currentValue.get(index)
            is AtomicArrayWithCAS2<*>.DCSSDescriptor -> currentValue.get()
            else -> currentValue
        } as E
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        return casInternal(index, expected, update)
    }

    private fun casInternal(index: Int, expected: E, update: Any): Boolean {
        while (true) {
            when (val currentValue = array[index].value) {
                this -> return true
                is AtomicArrayWithCAS2<*>.DCSSDescriptor -> {
                    // Help another DCSS to finish its work
                    currentValue.help()
                    continue
                }
                is AtomicArrayWithCAS2<*>.CAS2Descriptor -> {
                    currentValue.help()
                    continue
                }
                expected-> {
                    if (array[index].compareAndSet(expected, update)) {
                        return true
                    }
                }
                else -> return false
            }
        }
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

    private fun dcss(index: Int, expected: E, cas2descriptor: CAS2Descriptor, condition: () -> Boolean): Boolean {
        val descriptor = DCSSDescriptor(index, expected, cas2descriptor, condition)
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

        fun get(index: Int): E {
            val isFirstValue = index == index1
            return when (status.value) {
                UNDECIDED, FAILED -> if (isFirstValue) expected1 else expected2
                SUCCESS -> if (isFirstValue) update1 else update2
            }
        }

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            // TODO: In this task, only one thread can call cas2(..),
            // TODO: so cas2(..) calls cannot be executed concurrently.
            val success = installDescriptor()
            updateLogicalState(success)
            updatePhysically()
        }

        // Finish remaining work
        fun help() {
            apply()
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
                        currentValue.help()
                        continue
                    }
//                    is AtomicArrayWithCAS2<*>.DCSSDescriptor -> {
//                        // Help another CAS2 to finish its work
//                        currentValue.help()
//                        continue
//                    }
                    expected -> {
                        if (dcss(index, expected, this) { status.value == UNDECIDED }) {
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

    private inner class DCSSDescriptor(
        val index: Int,
        val expected: E,
        val update: CAS2Descriptor,
        val condition: () -> Boolean
    ) {
        val status = atomic(UNDECIDED)

        fun get(): E {
            return when (status.value) {
                UNDECIDED, FAILED -> expected
                SUCCESS -> update.get(index)
            }
        }

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            // TODO: In this task, only one thread can call cas2(..),
            // TODO: so cas2(..) calls cannot be executed concurrently.
            // If we failed to install descriptor, we didn't change anything,
            // so don't try to do anything.
            // `updateLogicalState` and `updatePhysically` assume that descriptor is installed.
            if (installDescriptor()) {
                updateLogicalState()
                updatePhysically()
            }
        }

        fun help() {
            updateLogicalState()
            updatePhysically()
        }

        private fun installDescriptor(): Boolean {
            return casInternal(index, expected, this)
        }

        private fun updateLogicalState() {
            val newStatus = if (condition()) SUCCESS else FAILED
            status.compareAndSet(UNDECIDED, newStatus)
        }

        private fun updatePhysically() {
            when (status.value) {
                SUCCESS -> {
                    array[index].compareAndSet(this, update)
                }
                FAILED -> {
                    array[index].compareAndSet(this, expected)
                }
                UNDECIDED -> error("Unexpected")
            }
        }
    }

    private enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}