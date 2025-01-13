@file:Suppress("DuplicatedCode")

//package day4

import AtomicCounterArray.Status.*
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
        val currentValue = array[index].value
        return if (currentValue is IncrementDescriptor) {
            if (currentValue.status.value == SUCCESS) {
                if (index == currentValue.index1) currentValue.valueBeforeIncrement1+1
                else currentValue.valueBeforeIncrement2+1
            } else {
                if (index == currentValue.index1) currentValue.valueBeforeIncrement1
                else currentValue.valueBeforeIncrement2
            }
        } else {
            currentValue as Int
        }
    }

    fun inc2(index1: Int, index2: Int) {
        require(index1 != index2) { "The indices should be different" }
        // TODO: This implementation is not linearizable!
        // TODO: Use `IncrementDescriptor` to perform the operation atomically.
        fun inc2(index1: Int, index2: Int) {
            require(index1 != index2) { "The indices should be different" }

            while (true) {
                // 1) Read the current cell states
                val valueBeforeIncrement1 = get(index1)
                val valueBeforeIncrement2 = get(index2)

                // 2) Create a new descriptor
                val descriptor = IncrementDescriptor(
                    index1, valueBeforeIncrement1,
                    index2, valueBeforeIncrement2,
                )

                // 3) Call `applyOperation()` -- it should try to increment the counters atomically.
                descriptor.applyOperation()

                // 4) Check whether the `status` is `SUCCESS` or `FAILED`, restarting in the latter case.
                if (descriptor.status.value == SUCCESS) {
                    break
                }
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
        val status = atomic(UNDECIDED)

        // TODO: Other threads can call this function
        // TODO: to help completing the operation.
        fun applyOperation() {
            // TODO: Use the CAS2 algorithm, installing this descriptor
            // TODO: in `array[index1]` and `array[index2]` cells.
            while (true) {

                array[index2].compareAndSet(valueBeforeIncrement2, this)

                when (val resultValue = array[index2].value) {
                    this -> {
                        status.compareAndSet(UNDECIDED, SUCCESS)
                    }
                    is IncrementDescriptor -> {
                        resultValue.applyOperation()
                        continue
                    }
                    valueBeforeIncrement2 + 1 -> {
                        if (status.value != SUCCESS) {
                            status.value = FAILED
                        }
                    }
                    else -> {
                        status.compareAndSet(UNDECIDED, FAILED)
                    }
                }

                when (status.value) {
                    UNDECIDED -> {
                        status.compareAndSet(UNDECIDED, FAILED)
                        continue
                    }
                    FAILED -> break
                    SUCCESS -> {}
                }

                array[index1].compareAndSet(this, valueBeforeIncrement1+1)
                if (array[index1].value == this) continue

                array[index2].compareAndSet(this, valueBeforeIncrement2+1)
                if (array[index2].value == this) continue

                break
            }

            if (status.value == FAILED) {
                array[index2].compareAndSet(this, valueBeforeIncrement2)
                array[index1].compareAndSet(this, valueBeforeIncrement1)
            }
        }
    }

    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}