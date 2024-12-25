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
        val value = array[index].value
        return if (value is IncrementDescriptor) {
            value.getValue(index)
        } else {
            value as Int
        }
    }

    fun inc2(index1: Int, index2: Int) {
        require(index1 != index2) { "The indices should be different" }

        var leftIndex = index1
        var rightIndex = index2
        if (index1 > index2) {
            leftIndex = index2
            rightIndex = index1
        }

        while (true) {
            val descriptor = IncrementDescriptor(leftIndex, get(leftIndex), rightIndex, get(rightIndex))

            descriptor.applyOperation()

            if (descriptor.status.value == SUCCESS) {
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
        val leftIndex: Int, val leftValueBeforeIncrement: Int,
        val rightIndex: Int, val rightValueBeforeIncrement: Int
    ) {
        val status = atomic(UNDECIDED)

        // TODO: Other threads can call this function
        // TODO: to help completing the operation.
        fun applyOperation() {
            val leftValue = array[leftIndex].value
            if (leftValue is IncrementDescriptor) {
                leftValue.applyOperation()
            }
            if (array[leftIndex].compareAndSet(leftValueBeforeIncrement, this)) {
                val rightValue = array[rightIndex].value
                if (rightValue is IncrementDescriptor) {
                    rightValue.applyOperation()
                }
                if (array[rightIndex].compareAndSet(rightValueBeforeIncrement, this)) {
                    status.value = SUCCESS
                    return
                }
            }
            array[leftIndex].compareAndSet(this, leftValueBeforeIncrement)
            array[rightIndex].compareAndSet(this, rightValueBeforeIncrement)
            status.value = FAILED
        }

        fun getValue (index: Int): Int {
            if (index == rightIndex) {
                return if (status.value == SUCCESS) rightValueBeforeIncrement + 1 else rightValueBeforeIncrement
            }
            return if (status.value == SUCCESS) leftValueBeforeIncrement + 1 else leftValueBeforeIncrement
        }
    }

    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}