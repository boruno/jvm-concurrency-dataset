@file:Suppress("DuplicatedCode")

//package day4

import day4.AtomicCounterArray.Status.*
import kotlinx.atomicfu.*
import javax.management.Descriptor

// This implementation never stores `null` values.
class AtomicCounterArray(size: Int) {
    private val array = atomicArrayOfNulls<Any>(size)

    init {
        // Fill array with zeros.
        for (i in 0 until size) {
            array[i].value = 0
        }
    }

    fun get(index: Int): Int {
        // TODO: the cell can store a descriptor.
        val currentValue = array[index].value
        return if (currentValue is IncrementDescriptor) {
            if (currentValue.status.value == SUCCESS) {
                if (index == currentValue.index1) currentValue.valueBeforeIncrement1+1
                else currentValue.valueBeforeIncrement2+1
            } else {
                if (index == currentValue.index1) currentValue.valueBeforeIncrement1
                else currentValue.valueBeforeIncrement2
            }
        } else {
            currentValue as Int
        }
    }

    fun inc2(index1: Int, index2: Int) {
        require(index1 != index2) { "The indices should be different" }
        // TODO: This implementation is not linearizable!
        // TODO: Use `IncrementDescriptor` to perform the operation atomically.
        val firstValue = array[index1].value
        val secondValue = array[index2].value
        while (true) {
            if (firstValue is IncrementDescriptor) {
                firstValue.applyOperation()
            } else {
                val descriptor = IncrementDescriptor(index1, firstValue as Int, index2, secondValue as Int)
                array[index1].compareAndSet(firstValue, descriptor)
                descriptor.applyOperation()
                return
            }
        }
    }

    // TODO: Implement the `inc2` operation using this descriptor.
    // TODO: 1) Read the current cell states
    // TODO: 2) Create a new descriptor
    // TODO: 3) Call `applyOperation()` -- it should try to increment the counters atomically.
    // TODO: 4) Check whether the `status` is `SUCCESS` or `FAILED`, restarting in the latter case.
    private inner class IncrementDescriptor(
        val index1: Int, val valueBeforeIncrement1: Int,
        val index2: Int, val valueBeforeIncrement2: Int
    ) {
        val status = atomic(UNDECIDED)

        // TODO: Other threads can call this function
        // TODO: to help completing the operation.
        fun applyOperation() {
            // TODO: Use the CAS2 algorithm, installing this descriptor
            // TODO: in `array[index1]` and `array[index2]` cells.

            val descriptor = array[index1].value as IncrementDescriptor

            // try to set second value
            array[index2].compareAndSet(descriptor.valueBeforeIncrement2, descriptor)

            // try to change status
            if (array[index2].value == descriptor) {
                descriptor.status.compareAndSet(UNDECIDED, SUCCESS)
            }
            else {
                descriptor.status.compareAndSet(UNDECIDED, FAILED)
            }

            if (descriptor.status.value == FAILED) return
            // physically change first value
            array[index1].compareAndSet(descriptor, descriptor.valueBeforeIncrement1+1)

            // physically change first value
            array[index2].compareAndSet(descriptor, descriptor.valueBeforeIncrement2+1)
        }
    }

    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}