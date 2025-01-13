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
        val dataInCell = array[index].value
        if (dataInCell is Int) return dataInCell

        val descriptor = dataInCell as IncrementDescriptor
        return if (descriptor.status.value == SUCCESS) {
            descriptor.applyOperation()
            array[index].value as Int
        } else {
            if (index == descriptor.index1) descriptor.valueBeforeIncrement1
            else descriptor.valueBeforeIncrement2
        }
    }

    private fun rollBackValues(descriptor: IncrementDescriptor) {
        array[descriptor.index1].compareAndSet(descriptor, descriptor.valueBeforeIncrement1)
        array[descriptor.index2].compareAndSet(descriptor, descriptor.valueBeforeIncrement2)
    }

    fun inc2(index1: Int, index2: Int) {
        require(index1 != index2) { "The indices should be different" }
        // TODO: This implementation is not linearizable!
        // TODO: Use `IncrementDescriptor` to perform the operation atomically.
        while (true) {
            val valueBefore1 = array[index1].value
            val valueBefore2 = array[index2].value
            if (valueBefore1 is IncrementDescriptor) {
                if (valueBefore1.status.value == FAILED || !valueBefore1.applyOperation()) {
                    rollBackValues(valueBefore1)
                    continue
                }
            }

            if (valueBefore2 is IncrementDescriptor) {
                if (valueBefore2.status.value == FAILED || !valueBefore2.applyOperation()) {
                    rollBackValues(valueBefore2)
                    continue
                }
            }

            val descriptor = IncrementDescriptor(
                index1, valueBefore1 as Int,
                index2, valueBefore2 as Int
            )

            if (descriptor.applyOperation()) {
                return
            } else {
                rollBackValues(descriptor)
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
        fun applyOperation(): Boolean {
            // TODO: Use the CAS2 algorithm, installing this descriptor
            // TODO: in `array[index1]` and `array[index2]` cells.
            val ind1 = index1
            val ind2 = index2
            while (true) {
                val value1 = array[ind1].value
                val value2 = array[ind1].value
                val newFirstValue = valueBeforeIncrement1 + 1
                val newSecondValue = valueBeforeIncrement2 + 1
                val state = status.value

                // check for status
                if (!array[ind1].compareAndSet(valueBeforeIncrement1, this)) {
                    if (value1 is Int) return false
                    if (value1 is IncrementDescriptor) {
                        return false
                    }

                    return false
                }
                if (!array[ind2].compareAndSet(valueBeforeIncrement2, this)) {
                    if (value2 is Int) return false
                    if (value2 is IncrementDescriptor) {
                        return false
                    }

                    // rollback
                    return if (status.compareAndSet(state, FAILED)) {
                        array[index1].compareAndSet(this, valueBeforeIncrement1)
                        array[index2].compareAndSet(this, valueBeforeIncrement2)
                        false
                    } else {
                        rollBackValues(this)
                        false
                    }
                }

                if (status.value == FAILED) return false
                val firstValueNow = array[index1].value

                if (!array[index1].compareAndSet(this, newFirstValue)) {
                    status.compareAndSet(state, FAILED)
//                    if (value1 is Int) return false
//                    if (value1 is IncrementDescriptor) {
//                        return false
//                    }

                    return false
                }
                if (array[index2].compareAndSet(this, newSecondValue)) {
                    status.compareAndSet(state, SUCCESS)
                    return true
                }
            }

        }
    }

    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}