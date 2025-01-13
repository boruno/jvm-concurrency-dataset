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
        return when (val value = array[index].value!!) {
            is IncrementDescriptor -> {
                val valueBeforeIncrement1 = value.valueBeforeIncrement1
                val valueBeforeIncrement2 = value.valueBeforeIncrement2

                if (value.status.value == SUCCESS) {
                    return when (index) {
                        value.index1 -> valueBeforeIncrement1 + 1
                        else -> valueBeforeIncrement2 + 1
                    }
                } else {
                    return when (index) {
                        value.index1 -> valueBeforeIncrement1
                        else -> valueBeforeIncrement2
                    }
                }
            }
            else -> value as Int
        }
    }

    fun inc2(index1: Int, index2: Int) {
        require(index1 != index2) { "The indices should be different" }
        // TODO: This implementation is not linearizable!
        // TODO: Use `IncrementDescriptor` to perform the operation atomically.
        while (true) {
            val valueBeforeIncrement1 = array[index1].value
            val valueBeforeIncrement2 = array[index2].value

            if (valueBeforeIncrement1 is IncrementDescriptor) {
                valueBeforeIncrement1.applyOperation()
                continue
            }

            if (valueBeforeIncrement2 is IncrementDescriptor) {
                valueBeforeIncrement2.applyOperation()
                continue
            }

            valueBeforeIncrement1 as Int
            valueBeforeIncrement2 as Int

            val descriptor = IncrementDescriptor(
                index1 = index1, valueBeforeIncrement1 = valueBeforeIncrement1,
                index2 = index2, valueBeforeIncrement2 = valueBeforeIncrement2,
            )

            descriptor.applyOperation()
            if (descriptor.status.value == SUCCESS) return
            if (descriptor.status.value == FAILED) return
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
            val (firstIndex, secondIndex) = listOf(index1, index2).sorted()

            if (!array[firstIndex].compareAndSet(valueBeforeIncrement1, this)) {
                status.compareAndSet(UNDECIDED, FAILED)
                return
            }

            if (!array[secondIndex].compareAndSet(valueBeforeIncrement2, this)) {
                status.compareAndSet(UNDECIDED, FAILED)
                return
            }

            status.compareAndSet(UNDECIDED, SUCCESS)

            array[firstIndex].compareAndSet(this, valueBeforeIncrement1 + 1)
            array[secondIndex].compareAndSet(this, valueBeforeIncrement2 + 1)

            // TODO: Use the CAS2 algorithm, installing this descriptor
            // TODO: in `array[index1]` and `array[index2]` cells.
        }
    }

    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}
