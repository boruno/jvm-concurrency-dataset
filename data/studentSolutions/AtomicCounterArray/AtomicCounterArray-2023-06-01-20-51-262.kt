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

    fun get(index: Int): Any {
        // TODO: the cell can store a descriptor.
        if (array[index].value is IncrementDescriptor) {
            while (true) {
                (array[index].value as IncrementDescriptor).applyOperation()
                break
            }
        }
        return array[index].value!!
    }

    fun inc2(index1: Int, index2: Int) {
        require(index1 != index2) { "The indices should be different" }
        // TODO: This implementation is not linearizable!
        // TODO: Use `IncrementDescriptor` to perform the operation atomically.
        while (true) {
            val val1 = get(index1)
            val val2 = get(index2)
            val descriptor = IncrementDescriptor(index1, val1, index2, val2)
            descriptor.applyOperation()
            if (descriptor.status.value == SUCCESS) return;
        }
        //array[index1].value = array[index1].value!! + 1
        //array[index2].value = array[index2].value!! + 1
    }

    // TODO: Implement the `inc2` operation using this descriptor.
    // TODO: 1) Read the current cell states
    // TODO: 2) Create a new descriptor
    // TODO: 3) Call `applyOperation()` -- it should try to increment the counters atomically.
    // TODO: 4) Check whether the `status` is `SUCCESS` or `FAILED`, restarting in the latter case.
    private inner class IncrementDescriptor(
        val index1: Int, val valueBeforeIncrement1: Any,
        val index2: Int, val valueBeforeIncrement2: Any
    ) {
        val status = atomic<Status>(UNDECIDED)

        // TODO: Other threads can call this function
        // TODO: to help completing the operation.
        fun applyOperation() {
            // TODO: Use the CAS2 algorithm, installing this descriptor
            // TODO: in `array[index1]` and `array[index2]` cells.
            while (true) {
                if(array[index1].compareAndSet(valueBeforeIncrement1, this)) {
                    if (array[index2].compareAndSet(valueBeforeIncrement2, this)) {
                        status.compareAndSet(UNDECIDED, SUCCESS)
                        array[index1].compareAndSet(valueBeforeIncrement1, valueBeforeIncrement1 as Int + 1)
                        array[index2].compareAndSet(valueBeforeIncrement2, valueBeforeIncrement2 as Int + 1)
                        break
                    } else {
                        status.compareAndSet(UNDECIDED, FAILED)
                        break
                    }
                } else {
                    status.compareAndSet(UNDECIDED, FAILED)
                    break
                }
            }
        }
    }

    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}
