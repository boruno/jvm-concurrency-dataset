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
        while (true) {
            val value = array[index].value!!
            if (value is Int) return value
            (value as IncrementDescriptor).applyOperation()
        }
    }

    fun inc2(index1: Int, index2: Int) {
        require(index1 != index2) { "The indices should be different" }
        if (index1 > index2) return inc2(index2, index1)

        // TODO: This implementation is not linearizable!
        // TODO: Use `IncrementDescriptor` to perform the operation atomically.
        while (true) {
            val valueBeforeIncrement1 = get(index1)
            val valueBeforeIncrement2 = get(index2)

            if (valueBeforeIncrement1 != get(index1)) continue

            val descriptor = IncrementDescriptor(
                index1 = index1, valueBeforeIncrement1 = valueBeforeIncrement1,
                index2 = index2, valueBeforeIncrement2 = valueBeforeIncrement2,
            )

            descriptor.applyOperation()
            if (descriptor.status.value == SUCCESS) return
        }
    }

    override fun toString(): String {
        val list = mutableListOf<Any?>()

        for (i in 0 until array.size) {
            list.add(array[i].value)
        }

        return "AtomicCounterArray($list)"
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
            if (status.value == FAILED) return
            if (status.value == SUCCESS) return commit()

            val value1 = array[index1].value
            val value2 = array[index2].value

            val nextValue1 = valueBeforeIncrement1 + 1
            val nextValue2 = valueBeforeIncrement2 + 1

            if (value1 == nextValue1 && value2 == nextValue2) {
                status.compareAndSet(UNDECIDED, FAILED)
                return
            }

            if (value1 != nextValue1 && value1 != this) {
                if (!array[index1].compareAndSet(valueBeforeIncrement1, this)) {
                    if (status.compareAndSet(UNDECIDED, FAILED)) cleanup()
                    return
                }
            }

            if (value2 != nextValue2 && value2 != this) {
                if (!array[index2].compareAndSet(valueBeforeIncrement2, this)) {
                    if (status.compareAndSet(UNDECIDED, FAILED)) cleanup()
                    return
                }
            }

            if (status.compareAndSet(UNDECIDED, SUCCESS)) commit()
            // TODO: Use the CAS2 algorithm, installing this descriptor
            // TODO: in `array[index1]` and `array[index2]` cells.
        }

        private fun cleanup() {
            array[index1].compareAndSet(this, valueBeforeIncrement1)
            array[index2].compareAndSet(this, valueBeforeIncrement2)
        }

        private fun commit() {
            array[index1].compareAndSet(this, valueBeforeIncrement1 + 1)
            array[index2].compareAndSet(this, valueBeforeIncrement2 + 1)
        }

        override fun toString(): String {
            return "IncrementDescriptor(${status.value}, index1=$index1, valueBeforeIncrement1=$valueBeforeIncrement1, index2=$index2, valueBeforeIncrement2=$valueBeforeIncrement2)"
        }
    }

    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}
