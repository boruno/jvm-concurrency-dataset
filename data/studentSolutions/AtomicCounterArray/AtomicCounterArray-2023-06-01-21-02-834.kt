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
        val value = array[index].value
        if (value is IncrementDescriptor) {
            return if (index == value.index1) {
                value.valueBeforeIncrement1
            } else {
                value.valueBeforeIncrement2
            }
        }
        return value as Int
    }

    fun inc2(index1: Int, index2: Int) {
        require(index1 != index2) { "The indices should be different" }
        // TODO: This implementation is not linearizable!
        // TODO: Use `IncrementDescriptor` to perform the operation atomically.
        while (true) {
            val lhs = array[index1].value
            val rhs = array[index2].value
            if (lhs is Int && rhs is Int) {
                val descriptor = IncrementDescriptor(index1, lhs, index2, rhs)
                descriptor.applyOperation()
                if (descriptor.status.value === SUCCESS) {
                    return
                }
            }
            if (lhs is IncrementDescriptor) {
                lhs.applyOperation()
                continue
            }
            if (rhs is IncrementDescriptor) {
                rhs.applyOperation()
            }
        }
    }

    // TODO: Implement the `inc2` operation using this descriptor.
    // TODO: 1) Read the current cell states
    // TODO: 2) Create a new descriptor
    // TODO: 3) Call `applyOperation()` -- it should try to increment the counters atomically.
    // TODO: 4) Check whether the `status` is `SUCCESS` or `FAILED`, restarting in the latter case.
    private inner class IncrementDescriptor(
        var index1: Int, var valueBeforeIncrement1: Int,
        var index2: Int, var valueBeforeIncrement2: Int
    ) {
        val status = atomic(UNDECIDED)

        // TODO: Other threads can call this function
        // TODO: to help completing the operation.
        fun applyOperation() {
            // TODO: Use the CAS2 algorithm, installing this descriptor
            // TODO: in `array[index1]` and `array[index2]` cells.
            if (index1 > index2) {
                index1 = index2.also { index2 = index1 }
                valueBeforeIncrement1 = valueBeforeIncrement2.also {
                    valueBeforeIncrement2 = valueBeforeIncrement1 }
            }

            if (array[index1].compareAndSet(valueBeforeIncrement1, this)
                && array[index2].compareAndSet(valueBeforeIncrement2, this)
            ) {
                if (status.compareAndSet(UNDECIDED, SUCCESS)) {
                    array[index1].compareAndSet(this, valueBeforeIncrement1 + 1)
                    array[index2].compareAndSet(this, valueBeforeIncrement2 + 1)
                }
            } else {
                if (status.compareAndSet(UNDECIDED, FAILED)) {
                    array[index1].compareAndSet(this, valueBeforeIncrement1)
                    array[index2].compareAndSet(this, valueBeforeIncrement2)
                }
            }
        }
    }

    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}

fun main() {
    val array = AtomicCounterArray(10)
    array.inc2(0, 1)
    array.inc2(1, 2)
    println(array)
}