@file:Suppress("DuplicatedCode")

//package day4

import day4.AtomicCounterArray.Status.*
import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class AtomicCounterArray(size: Int) {
    private val array = atomicArrayOfNulls<Int>(size)

    init {
        // Fill array with zeros.
        for (i in 0 until size) {
            array[i].value = 0
        }
    }

    fun get(index: Int): Int {
        // TODO: the cell can store a descriptor.
        return array[index].value!!
    }

    fun inc2(index1: Int, index2: Int) {
        require(index1 != index2) { "The indices should be different" }
        // TODO: This implementation is not linearizable!
        // TODO: Use `IncrementDescriptor` to perform the operation atomically.
        while (true) {
            val cell1 = get(index1)
            val cell2 = get(index2)
            val desc = IncrementDescriptor(index1, cell1, index2, cell2)
            desc.applyOperation()
            if (desc.status.value == SUCCESS) {
                return
            } else if (desc.status.value == FAILED) {
                continue
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
            while (true) {
                val res1 = array[index1].compareAndSet(valueBeforeIncrement1, valueBeforeIncrement1.inc())
                val res2 = array[index2].compareAndSet(valueBeforeIncrement2, valueBeforeIncrement2.inc())
                if (res1 && res2) {
                    status.compareAndSet(UNDECIDED,SUCCESS)
                    status.compareAndSet(FAILED,SUCCESS)
                    return
                } else {
                    status.compareAndSet(UNDECIDED,FAILED)
                    continue
                }
            }
            // TODO: Use the CAS2 algorithm, installing this descriptor
            // TODO: in `array[index1]` and `array[index2]` cells.
        }
    }

    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}