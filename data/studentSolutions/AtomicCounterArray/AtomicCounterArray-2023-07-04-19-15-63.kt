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

    fun inc2(index1: Int, index2: Int): Boolean {
        require(index1 != index2) { "The indices should be different" }
        var desc: IncrementDescriptor

        while (true){
            val firstValue = get(index1)
            val secondValue = get(index2)
            desc = IncrementDescriptor(index1, firstValue, index2, secondValue)
            if (array[index1].compareAndSet(firstValue, desc)){
                desc.applyOperation()
                break
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
                val secondValue = get(index2)
                if (secondValue == valueBeforeIncrement2) {
                    if (array[index2].compareAndSet(secondValue, this)) {
                        // SUCCESS
                        status.compareAndSet(UNDECIDED, SUCCESS)
                        break
                    }
                    if (array[index2].value is IncrementDescriptor ){
                        if (array[index2].value == this){
                            // SUCCESS
                            status.compareAndSet(UNDECIDED, SUCCESS)
                            break
                        }
                        continue
                    }
                    else{
                        // FAILED another Int
                        status.compareAndSet(UNDECIDED, FAILED)
                        break
                    }
                }
                else{
                    // FAILED another Int
                    status.compareAndSet(UNDECIDED, FAILED)
                    break
                }
            }
            if (status.value === SUCCESS){
                array[index1].compareAndSet(this, valueBeforeIncrement1 + 1)
                array[index2].compareAndSet(this, valueBeforeIncrement2 + 1)
            }
            if (status.value === FAILED){
                array[index1].compareAndSet(this, valueBeforeIncrement1)
                array[index2].compareAndSet(this, valueBeforeIncrement2)
            }
        }
    }

    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}