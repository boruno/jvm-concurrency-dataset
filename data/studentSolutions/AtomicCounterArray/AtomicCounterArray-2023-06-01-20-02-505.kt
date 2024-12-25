@file:Suppress("DuplicatedCode")

//package day4

import day4.AtomicCounterArray.Status.*
import kotlinx.atomicfu.*

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
        val value = array[index].value!!
        if (value is IncrementDescriptor) {
            val result = value.getValueByIndex(index)
            if (value.status.value == UNDECIDED || value.status.value == FAILED) {
                return result
            }
            else {
                return result + 1
            }
        }
        else {
            return value as Int
        }
    }

    fun inc2(index1: Int, index2: Int) {
        require(index1 != index2) { "The indices should be different" }
        // TODO: This implementation is not linearizable!
        // TODO: Use `IncrementDescriptor` to perform the operation atomically.
        while (true) {
            val value1 = array[index1].value
            val value2 = array[index2].value
            if (value1 is IncrementDescriptor) {
                value1.applyOperation()
                continue
            }
            if (value2 is IncrementDescriptor) {
                value2.applyOperation()
                continue
            }
            val descriptor = IncrementDescriptor(index1, value1 as Int, index2, value2 as Int)
            if (array[index1].compareAndSet(value1, descriptor)) {
                if (array[index2].compareAndSet(value2, descriptor)) {
                    descriptor.applyOperation()
                    break
                }
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
            status.value = SUCCESS
            array[index1].compareAndSet(this, valueBeforeIncrement1 + 1)
            array[index2].compareAndSet(this, valueBeforeIncrement2 + 1)
        }

        fun getValueByIndex(index: Int): Int {
            if (index == index1) {
                return valueBeforeIncrement1
            }
            return valueBeforeIncrement2
        }
    }

    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}