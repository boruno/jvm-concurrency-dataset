@file:Suppress("DuplicatedCode")

package day4

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
        while(true) {
            val cur = array[index].value!!
            if (cur is IncrementDescriptor) {
                cur.applyOperation()
                continue
            }
            if (array[index].value !is Int) throw Exception("no int4")
            return array[index].value!!
        }
    }

    fun inc2(index1: Int, index2: Int) {
        require(index1 != index2) { "The indices should be different" }
        // TODO: This implementation is not linearizable!
        // TODO: Use `IncrementDescriptor` to perform the operation atomically.
        var counter = 0
        while (true) {
            val cur1 = get(index1)
            val cur2 = get(index2)
            val descriptor = IncrementDescriptor(index1, cur1 as Int, index2, cur2 as Int)
            descriptor.applyOperation()
            if (descriptor.status.value == SUCCESS) {
                if (array[index1].value !is Int) throw Exception("no int3")
                if (array[index2].value !is Int) throw Exception("no int3")
                return
            }
            counter += 1
            if (counter > 10000) {
                throw Exception("BUSTED")
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
                val val1 = array[index1].value
                val val2 = array[index2].value
                array[index1].compareAndSet(valueBeforeIncrement1, this)
                array[index2].compareAndSet(valueBeforeIncrement2, this)
                if (val1 != this && val1 != valueBeforeIncrement1 || val2 != this && val2 != valueBeforeIncrement2) {
                    array[index1].compareAndSet(this, valueBeforeIncrement1)
                    array[index2].compareAndSet(this, valueBeforeIncrement2)
                    status.compareAndSet(UNDECIDED, FAILED)
                    return
                }
                status.compareAndSet(UNDECIDED, SUCCESS)
                array[index1].compareAndSet(this, valueBeforeIncrement1 + 1)
                array[index2].compareAndSet(this, valueBeforeIncrement2 + 1)
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