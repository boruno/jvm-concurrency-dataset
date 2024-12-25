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
        val result = array[index].value
        if (result is Int) {
            return result
        } else {
            val descriptor = result as IncrementDescriptor
            descriptor.applyOperation()
            if (descriptor.status.value == SUCCESS) {
                if (index == descriptor.index1) {
                    return descriptor.valueBeforeIncrement1 + 1
                } else if (index == descriptor.index2) {
                    return descriptor.valueBeforeIncrement2 + 1
                }
            } else if(descriptor.status.value == FAILED){
                if (index == descriptor.index1) {
                    return descriptor.valueBeforeIncrement1
                } else if (index == descriptor.index2) {
                    return descriptor.valueBeforeIncrement2
                }
            }
            check(false) // should never happen
        }
        return -1
    }

    fun inc2(index1: Int, index2: Int) {
        require(index1 != index2) { "The indices should be different" }
        while (true) {
            // TODO: This implementation is not linearizable!
            // TODO: Use `IncrementDescriptor` to perform the operation atomically.
            val value1 = get(index1)
            val value2 = get(index2)
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
    private inner class IncrementDescriptor {
        val index1: Int
        val valueBeforeIncrement1: Int
        val index2: Int
        val valueBeforeIncrement2: Int

        val status = atomic(UNDECIDED)

        constructor(index1: Int, valueBeforeIncrement1: Int, index2: Int, valueBeforeIncrement2: Int) {
            if (index1 < index2) {
                this.index1 = index1
                this.valueBeforeIncrement1 = valueBeforeIncrement1
                this.index2 = index2
                this.valueBeforeIncrement2 = valueBeforeIncrement2
            } else {
                this.index1 = index2
                this.valueBeforeIncrement1 = valueBeforeIncrement2
                this.index2 = index1
                this.valueBeforeIncrement2 = valueBeforeIncrement1
            }
        }

        // TODO: Other threads can call this function
        // TODO: to help completing the operation.
        fun applyOperation() {
            // TODO: Use the CAS2 algorithm, installing this descriptor
            // TODO: in `array[index1]` and `array[index2]` cells.
            if (status.value == SUCCESS) {
                array[index2].compareAndSet(this, valueBeforeIncrement2 + 1)
                array[index1].compareAndSet(this, valueBeforeIncrement1 + 1)
            }
            else if (status.value == FAILED) {
                array[index2].compareAndSet(this, valueBeforeIncrement2)
                array[index1].compareAndSet(this, valueBeforeIncrement1)
            } else {
                if (array[index1].compareAndSet(valueBeforeIncrement1, this) && array[index2].compareAndSet(valueBeforeIncrement2, this)) {
                    status.compareAndSet(UNDECIDED, SUCCESS)
                } else {
                    status.compareAndSet(FAILED, SUCCESS)
                }
            }
        }

    }

    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}