@file:Suppress("DuplicatedCode")

package day3

import day3.AtomicCounterArray.Status.*
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
        return array[index].value!! as Int
    }

    fun inc2(index1: Int, index2: Int) {
        require(index1 != index2) { "The indices should be different" }
        // TODO: This implementation is not linearizable!
        // TODO: Use `IncrementDescriptor` to perform the operation atomically.
        while (true) {
            val cur1 = array[index1].value
            val cur2 = array[index2].value
            val descriptor = if (cur1 is Int) {
                if (cur2 is Int) {
                    IncrementDescriptor(index1, cur1, index2, cur2)
                } else {
                    cur2 as IncrementDescriptor
                }
            } else {
                cur1 as IncrementDescriptor
            }
            descriptor.applyOperation()
            if (descriptor.status.value != FAILED) {
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
        val status = atomic<Status>(UNDECIDED)

        // TODO: Other threads can call this function
        // TODO: to help completing the operation.
        fun applyOperation() {
            if (status.value == UNDECIDED) {
                array[index1].compareAndSet(valueBeforeIncrement1, this)
                array[index2].compareAndSet(valueBeforeIncrement2, this)
                status.compareAndSet(UNDECIDED, SUCCESS)
                return
            }
            if (status.value == SUCCESS) {
                array[index1].compareAndSet(this, valueBeforeIncrement1 + 1)
                array[index2].compareAndSet(this, valueBeforeIncrement2 + 1)
                return
            }
            array[index1].compareAndSet(this, valueBeforeIncrement1)
            array[index2].compareAndSet(this, valueBeforeIncrement2)
        }
    }

    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}