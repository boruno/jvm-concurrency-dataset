@file:Suppress("DuplicatedCode")

package day4

import day4.AtomicCounterArray.Status.*
import kotlinx.atomicfu.*
import javax.management.Descriptor

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

        val curRes = array[index].value
        if (curRes is Int) return curRes
        val curDescriptor = curRes as IncrementDescriptor

        val stat = curDescriptor.status.value


        return if (stat == SUCCESS) {
            1 + if (curDescriptor.index1 == index) curDescriptor.valueBeforeIncrement1
            else curDescriptor.valueBeforeIncrement2
        } else {
            if (curDescriptor.index1 == index) curDescriptor.valueBeforeIncrement1
            else curDescriptor.valueBeforeIncrement2
        }


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
            val res = descriptor.status.value
            if (res == SUCCESS) return
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
                val elem1 = array[index1].value
                if (elem1 is IncrementDescriptor && elem1 != this) {
                    elem1.applyOperation()
                    continue
                }

                val elem2 = array[index2].value
                if (elem2 is IncrementDescriptor && elem2 != this) {
                    elem2.applyOperation()
                    continue
                }

                if (!array[index1].compareAndSet(valueBeforeIncrement1, this) &&
                    elem1 != this) {
                    return
                }

                if (!array[index2].compareAndSet(valueBeforeIncrement2, this) && elem2 != this) {
                    this.status.compareAndSet(UNDECIDED, FAILED)
                    array[index1].compareAndSet(this, valueBeforeIncrement1)
                    return
                }

                this.status.compareAndSet(UNDECIDED, SUCCESS)
                array[index1].compareAndSet(this, valueBeforeIncrement1 + 1)
                array[index2].compareAndSet(this, valueBeforeIncrement2 + 1)
                return
            }
        }
    }

    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}