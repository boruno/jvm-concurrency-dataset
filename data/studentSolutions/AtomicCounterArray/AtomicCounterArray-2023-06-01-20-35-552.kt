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
        while (true) {
            val v = array[index].value
            if (v is IncrementDescriptor) {
                v.applyOperation()
                continue
            }
            return v as Int
        }
    }

    fun inc2(index1: Int, index2: Int) {
        require(index1 != index2) { "The indices should be different" }
        // TODO: This implementation is not linearizable!
        // TODO: Use `IncrementDescriptor` to perform the operation atomically.
        val (ind1, ind2) = if (index1 < index2) index1 to index2 else index2 to index1
        while (true) {
            val v1 = get(ind1)
            val v2 = get(ind2)
            val descriptor = IncrementDescriptor(ind1, v1, ind2, v2)
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
            val v1 = array[index1].value
            val v2 = array[index2].value
            if (v1 == this || v1 == valueBeforeIncrement1 + 1) {
                if (v2 == this) {
                    if (status.value == SUCCESS || status.value == UNDECIDED) {
                        status.value = SUCCESS
                        array[index1].compareAndSet(this, valueBeforeIncrement1 + 1)
                        array[index2].compareAndSet(this, valueBeforeIncrement2 + 1)
                    }
                    if (status.value == FAILED) {
                        array[index1].compareAndSet(this, valueBeforeIncrement1)
                        array[index2].compareAndSet(this, valueBeforeIncrement2)
                    }
                    return
                }
            }
            while (true) {
                if (array[index1].compareAndSet(valueBeforeIncrement1, this)) {
                    if (array[index2].compareAndSet(valueBeforeIncrement2, this)) {
                        if (status.compareAndSet(UNDECIDED, SUCCESS)) {
                            array[index1].compareAndSet(this, valueBeforeIncrement1 + 1)
                            array[index2].compareAndSet(this, valueBeforeIncrement2 + 1)
                        }
                        return
                    }
                    else {
                        if (status.compareAndSet(UNDECIDED, FAILED)) {
                            if (array[index1].compareAndSet(this, valueBeforeIncrement1)) {
                                val v1 = array[index1].value
                                if (v1 is IncrementDescriptor) {
                                    v1.applyOperation()
                                    continue
                                }
                            }
                        }
                        val v2 = array[index2].value
                        if (v2 is IncrementDescriptor) {
                            v2.applyOperation()
                            continue
                        }
                        else return
                    }
                }
                else {
                    val v1 = array[index1].value
                    if (v1 is IncrementDescriptor) {
                        v1.applyOperation()
                        continue
                    }
                    else {
                        status.compareAndSet(UNDECIDED, FAILED)
                        return
                    }
                }
            }
        }
    }

    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}