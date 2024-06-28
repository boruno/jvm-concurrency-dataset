@file:Suppress("DuplicatedCode")

package day4

import day4.AtomicCounterArray.Status.*
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

    fun get(index: Int): Int {
        // TODO: the cell can store a descriptor.
        require(index >= 0 && index < array.size)
        var i = 0
        while (i < array.size){
            print(array[i].value)
            i++
        }
        while (true){
            val k = array[index].value
            if (k is IncrementDescriptor){
                k.applyOperation()
                continue
            }
            else {
                print(" ($index, $k) -- ")
                var i = 0
                while (i < array.size){
                    print(array[i].value)
                    i++
                }
                println()
                return k as Int
            }
        }
    }

    fun anotherGet(index: Int): Int {
        val k = array[index].value
        if (k is IncrementDescriptor) {
            if (index == k.index1){
                if (k.status.value === SUCCESS)
                    return k.valueBeforeIncrement1 + 1
                else
                    return k.valueBeforeIncrement1
            }
            else{
                if (k.status.value === SUCCESS)
                    return k.valueBeforeIncrement2 + 1
                else
                    return k.valueBeforeIncrement2
            }
        }
        return k as Int
    }

    fun inc2(index1: Int, index2: Int): Boolean {
        print("Inc2 works! ")
        if (index1 == index2)
            println(" aboba ")
        require(index1 != index2) { "The indices should be different" }
        var desc: IncrementDescriptor
        var i = 0
        while (i < array.size){
            print(array[i].value)
            i++ }
        print("--i1:$index1, i2:$index2 Before Incr: ${array[index1].value}, ${array[index2].value} ")
        while (true){
            val firstValue = array[index1].value
            if (firstValue is IncrementDescriptor){
                firstValue.applyOperation()
                continue
            }
            val secondValue = array[index2].value
            if (secondValue is IncrementDescriptor){
                secondValue.applyOperation()
                continue
            }
            desc = IncrementDescriptor(index1, firstValue as Int, index2, secondValue as Int)
            if (array[index1].compareAndSet(firstValue, desc)){
                if (array[index2].compareAndSet(secondValue, desc)){
                    desc.applyOperation()
                    break
                }
                continue
            }
            continue
        }
        println("After: ${array[index1].value}, ${array[index2].value} -- ${desc.status.value}")
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
            print(" -apply- ")
            // TODO: Use the CAS2 algorithm, installing this descriptor
            // TODO: in `array[index1]` and `array[index2]` cells.
            val val1 = anotherGet(index1)
            val val2 = anotherGet(index2)
            if (val1 == valueBeforeIncrement1 && val2 == valueBeforeIncrement2)
                status.compareAndSet(UNDECIDED, SUCCESS)
            else
                status.compareAndSet(UNDECIDED, FAILED)
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