@file:Suppress("DuplicatedCode")

//package day4

import AtomicCounterArray.Status.*
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
        //val element = array[index].value
        //while(true) {
        //    if (array[index].value == element) {
        //        return element!!
        //    }
        //}
    }

    fun inc2(index1: Int, index2: Int) {
        require(index1 != index2) { "The indices should be different" }
        // TODO: This implementation is not linearizable!
        // TODO: Use `IncrementDescriptor` to perform the operation atomically.
        while(true) {
            val val1 = get(index1)
            val val2 = get(index2)
            val newDescriptor = IncrementDescriptor(index1, val1, index2 as Int, val2 as Int)
            newDescriptor.applyOperation()
            if (newDescriptor.status.value === SUCCESS) {
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
            val element1 = get(index1)
            val element2 = get(index2)
            if (element1 != valueBeforeIncrement1 || element2 != valueBeforeIncrement2) {
                status.value = FAILED
                return
            }
            while(status.value == UNDECIDED) {
                if (array[index1].compareAndSet(element1,element1 + 1) &&
                    array[index2].compareAndSet(element2,element2 + 1)) {
                    status.value = SUCCESS
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