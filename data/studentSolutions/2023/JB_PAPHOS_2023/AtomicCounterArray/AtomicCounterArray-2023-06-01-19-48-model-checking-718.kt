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

    fun get(index: Int): Int {
        while (true) {
            val v = array[index].value
            if (v is IncrementDescriptor) {
                v.applyOperation()
            } else {
                return v as Int
            }
        }
    }

    fun inc2(index1: Int, index2: Int) {
        require(index1 != index2) { "The indices should be different" }
        val (min, max) = if (index1 < index2) index1 to index2 else index2 to index1
        while (true) {
            val minVal = array[min].value
            if (minVal is IncrementDescriptor) {
                minVal.applyOperation()
                continue
            }
            val maxVal = array[max].value
            if (maxVal is IncrementDescriptor) {
                maxVal.applyOperation()
                continue
            }
            IncrementDescriptor(min, minVal as Int, max, maxVal as Int).applyOperation()
            return
        }
    }

    private inner class IncrementDescriptor(
        val index1: Int, val valueBeforeIncrement1: Int,
        val index2: Int, val valueBeforeIncrement2: Int
    ) {
        val status = atomic(UNDECIDED)

        fun applyOperation() {
            if (array[index1].value != this && !array[index1].compareAndSet(valueBeforeIncrement1, this)) {
                status.compareAndSet(UNDECIDED, FAILED)
            } else if (array[index2].value != this && !array[index2].compareAndSet(valueBeforeIncrement2, this)) {
                status.compareAndSet(UNDECIDED, FAILED)
            } else if (status.compareAndSet(UNDECIDED, SUCCESS)) {
                array[index1].value = valueBeforeIncrement1 + 1
                array[index2].value = valueBeforeIncrement2 + 1
            }
        }
    }

    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}
//
//fun main() {
//    val ar = AtomicCounterArray(3)
//    ar.inc2(1, 2)
//    println(ar.get(2))
//}