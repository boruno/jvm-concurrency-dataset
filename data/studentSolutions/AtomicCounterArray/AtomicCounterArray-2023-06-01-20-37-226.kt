@file:Suppress("DuplicatedCode")

//package day4

import AtomicCounterArray.Status.*
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
        if (index1 > index2) inc2(index2, index1)
        // TODO: This implementation is not linearizable!
        // TODO: Use `IncrementDescriptor` to perform the operation atomically.
        while (true) {
            val value1 = array[index1].value
            if (value1 is IncrementDescriptor) {
                value1.applyOperation()
                continue
            }
            value1 as Int

            val value2 = array[index2].value
            if (value2 is IncrementDescriptor) {
                value2.applyOperation()
                continue
            }
            value2 as Int

            val descriptor = IncrementDescriptor(index1, value1, index2, value2)
            descriptor.applyOperation()

            if (descriptor.status.value == SUCCESS) return
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
            while (true) {
                if (array[index1].compareAndSet(valueBeforeIncrement1, this)) {
                    if (array[index2].compareAndSet(valueBeforeIncrement2, this)) {
                        status.value = SUCCESS
                        array[index1].compareAndSet(this, valueBeforeIncrement1 + 1)
                        array[index2].compareAndSet(this, valueBeforeIncrement2 + 1)
                        break
                    }
                    else {
                        status.value = FAILED
                        return
                    }
                }
                else {
                    status.value = FAILED
                    return
                }
            }
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