@file:Suppress("DuplicatedCode")

//package day4

import day4.AtomicCounterArray.Status.*
import kotlinx.atomicfu.*

private fun Int.incIf(b: Boolean): Int {
    return if (b) {
        this.inc()
    } else {
        this
    }
}

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
        val value = array[index].value
        return if (value is IncrementDescriptor) {
            value.valueOf(index)
        } else {
            value as Int
        }
    }

    fun inc2(index1: Int, index2: Int) {
        require(index1 != index2) { "The indices should be different" }
        // TODO: This implementation is not linearizable!
        // TODO: Use `IncrementDescriptor` to perform the operation atomically.
        do {
            val descriptor = createDescriptor(index1, index2)
            descriptor.applyOperation()
        } while (descriptor.status.value != SUCCESS)
    }

    private fun createDescriptor(index1: Int, index2: Int): IncrementDescriptor {
        val firstIndex = minOf(index1, index2)
        val secondIndex = maxOf(index1, index2)
        return IncrementDescriptor(firstIndex, get(firstIndex), secondIndex, get(secondIndex))
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
            if (status.value != UNDECIDED) {
                val isSuccess = status.value == SUCCESS
                array[index1].compareAndSet(valueBeforeIncrement1, valueBeforeIncrement1.incIf(isSuccess))
                array[index2].compareAndSet(valueBeforeIncrement2, valueBeforeIncrement2.incIf(isSuccess))
            }
            if (applyIndex(index1, valueBeforeIncrement1) &&
                applyIndex(index2, valueBeforeIncrement2)
            ) {
                status.compareAndSet(UNDECIDED, SUCCESS)
            }
            array[index1].compareAndSet(this, get(index1))
            array[index1].compareAndSet(this, get(index2))
            // TODO: Use the CAS2 algorithm, installing this descriptor
            // TODO: in `array[index1]` and `array[index2]` cells.
        }

        private fun applyIndex(index1: Int, before: Int): Boolean {
            do {
                val cur = array[index1].value
                if (cur is IncrementDescriptor) {
                    if (cur == this) {
                        break
                    } else {
                        if (cur.status.value == UNDECIDED) {
                            cur.applyOperation()
                        }
                    }
                } else if (cur is Int) {
                    if (cur != before) {
                        status.compareAndSet(UNDECIDED, FAILED)
                        return false
                    }
                }
            } while (status.value == UNDECIDED && array[index1].compareAndSet(before, this))
            return true
        }

        fun valueOf(index: Int): Int {
            return if (index == index1) {
                valueBeforeIncrement1
            } else {
                valueBeforeIncrement2
            }.let {
                if (status.value == SUCCESS) {
                    it + 1
                } else {
                    it
                }
            }
        }
    }

    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}