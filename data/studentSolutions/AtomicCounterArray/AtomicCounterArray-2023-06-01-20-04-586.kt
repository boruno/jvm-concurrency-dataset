@file:Suppress("DuplicatedCode")

//package day4

import day4.AtomicCounterArray.Status.*
import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class AtomicCounterArray(size: Int) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with zeros.
        for (i in 0 until size) {
            array[i].value = 0
        }
    }

    fun get(index: Int): Int {
        // TODO: the cell can store a descriptor.
        while (true) {
            val value = array[index].value
            if (value is IncrementDescriptor) {
                value.applyOperation()
                continue

            } else {
                return value as Int
            }
        }
    }

    fun inc2(index1: Int, index2: Int) {
        require(index1 != index2) { "The indices should be different" }
        // TODO: This implementation is not linearizable!
        // TODO: Use `IncrementDescriptor` to perform the operation atomically.
        val idx1 = if (index1 < index2) index1 else index2
        val idx2 = if (index1 < index2) index2 else index1
        val valueBeforeIncrement1 = get(idx1)
        val valueBeforeIncrement2 = get(idx2)
        val descr = IncrementDescriptor(idx1, valueBeforeIncrement1, idx2, valueBeforeIncrement2)
        descr.applyOperation()
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
            if (array[index1].compareAndSet(valueBeforeIncrement1, valueBeforeIncrement1 + 1)) {
                if (array[index2].compareAndSet(valueBeforeIncrement2, valueBeforeIncrement2 + 1)) {
                    status.value = SUCCESS
                    return
                } else {
                    status.value = FAILED
                    array[index1].compareAndSet(valueBeforeIncrement1 + 1, valueBeforeIncrement1)
                }
            }
        }
    }

    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}