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
        while (true) {
            when (val value = array[index].value) {
                is Int -> return value
                is IncrementDescriptor -> value.applyOperation()
            }
        }
    }

    fun inc2(index1: Int, index2: Int) {
        require(index1 != index2) { "The indices should be different" }
        // TODO: This implementation is not linearizable!
        // TODO: Use `IncrementDescriptor` to perform the operation atomically.
        do {
            val value1 = get(index1)
            val value2 = get(index2)
            val descriptor = IncrementDescriptor(index1, value1, index2, value2)
            descriptor.applyOperation()
        } while (descriptor.status.value != SUCCESS)
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
            // Try set descriptor
            if(!array[index1].compareAndSet(valueBeforeIncrement1, this)) {
                status.compareAndSet(UNDECIDED, FAILED)
                return
            }
            if(!array[index2].compareAndSet(valueBeforeIncrement2, this)) {
                status.compareAndSet(UNDECIDED, FAILED)
                return
            }
            status.compareAndSet(UNDECIDED, SUCCESS)

            // Update cell to value
            array[index1].compareAndSet(this, valueBeforeIncrement1 + 1)
            array[index2].compareAndSet(this, valueBeforeIncrement2 + 1)
        }
    }

    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}