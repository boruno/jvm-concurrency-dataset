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
        while (true) {
            val value = array[index].value!!
            return if (value is IncrementDescriptor) {
//                value.applyOperation()
                (if (value.index1 == index) value.valueBeforeIncrement1 else value.valueBeforeIncrement2) +
                        if (value.status.value == SUCCESS) 1 else 0
            } else {
                value as Int
            }
        }
    }

    fun inc2(index1: Int, index2: Int) {
        require(index1 != index2) { "The indices should be different" }
        // TODO: This implementation is not linearizable!
        // TODO: Use `IncrementDescriptor` to perform the operation atomically.
        val (indexFirst, indexSecond) = listOf(index1, index2).sorted()
        while (true) {
            val value1 = get(indexFirst)
            val value2 = get(indexSecond)
            val desc = IncrementDescriptor(indexFirst, value1, indexSecond, value2)
            val v1 = array[indexFirst].value
            if (array[indexFirst].compareAndSet(value1, desc)) {
                desc.applyOperation()
                if (desc.status.value == SUCCESS) {
                    return
                }
            } else if (v1 is IncrementDescriptor) {
                v1.applyOperation()
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
            while (status.value == UNDECIDED) {
                val secondValue = array[index2].value
                if (!array[index2].compareAndSet(valueBeforeIncrement2, this) && secondValue !== this) {
                    if (secondValue is IncrementDescriptor) {
                        secondValue.applyOperation()
                    } else {
                        status.value = FAILED
                        break
                    }
                } else {
                    status.value = SUCCESS
                    break
                }
            }

            if (status.value == SUCCESS) {
                array[index1].compareAndSet(this, valueBeforeIncrement1 + 1)
                array[index2].compareAndSet(this, valueBeforeIncrement2 + 1)
            } else {
                array[index1].compareAndSet(this, valueBeforeIncrement1)
                array[index2].compareAndSet(this, valueBeforeIncrement2)
            }
        }
    }

    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}