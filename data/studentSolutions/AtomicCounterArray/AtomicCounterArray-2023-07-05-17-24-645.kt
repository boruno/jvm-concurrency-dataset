@file:Suppress("DuplicatedCode")

//package day4

import AtomicCounterArray.Status.*
import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class AtomicCounterArray(size: Int) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with zeros.
        for (i in 0 until size) {
            array[i].value = 0
        }
        println()
    }

    fun anotherGet(index: Int): Int {
        // TODO: the cell can store a descriptor.
        require(index >= 0 && index < array.size)
        while (true){
            val k = array[index].value
            if (k is IncrementDescriptor){
                k.applyOperation()
                continue
            }
            else {
                return k as Int
            }
        }
    }

    fun get(index: Int): Int {
        require(index >= 0 && index < array.size)
        val currentValue = array[index].value
        if (currentValue is IncrementDescriptor) {
            if (currentValue.status.value == SUCCESS) {
                if (index == currentValue.index1) return currentValue.valueBeforeIncrement1 + 1
                else return currentValue.valueBeforeIncrement2 + 1
            } else {
                if (index == currentValue.index1) return currentValue.valueBeforeIncrement1
                else return currentValue.valueBeforeIncrement2
            }
        } else {
            return currentValue as Int
        }
    }

    fun inc2(index1: Int, index2: Int): Boolean {
        require(index1 != index2) { "The indices should be different" }
        var desc: IncrementDescriptor

        while (true){
            val firstValue = array[index1].value
            val secondValue = array[index2].value
            if (firstValue is IncrementDescriptor){
                firstValue.applyOperation()
                continue
            }
            if (secondValue is IncrementDescriptor){
                secondValue.applyOperation()
                continue
            }
            if (index1 < index2)
                desc = IncrementDescriptor(index1, firstValue as Int, index2, secondValue as Int)
            else
                desc = IncrementDescriptor(index2, secondValue as Int, index1, firstValue as Int)
            if (array[index1].compareAndSet(firstValue, desc)){
                desc.applyOperation()
                break
            }
            val minInd = if (index1 < index2) index1 else index2
            val minIndValue = if (index1 < index2) firstValue else secondValue
            if (array[minInd].compareAndSet(minIndValue, desc)) {
                desc.applyOperation()
                if (desc.status.value == SUCCESS)
                    return true
                return false
            }

        }

        if (desc.status.value === SUCCESS) {
            return true
        }
        return false
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