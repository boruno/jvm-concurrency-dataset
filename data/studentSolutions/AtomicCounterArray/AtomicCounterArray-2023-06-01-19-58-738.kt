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
        val minVal = get(min)
        val maxVal = get(max)
        IncrementDescriptor(min, minVal, max, maxVal).applyOperation()
    }

    private inner class IncrementDescriptor(
        val index1: Int, val valueBeforeIncrement1: Int,
        val index2: Int, val valueBeforeIncrement2: Int
    ) {
        val status = atomic(UNDECIDED)

        fun applyOperation() {
            when (status.value) {
                FAILED -> {
                    array[index1].compareAndSet(this, valueBeforeIncrement1)
                    array[index2].compareAndSet(this, valueBeforeIncrement2)
                }
                SUCCESS -> {
                    array[index1].compareAndSet(this, valueBeforeIncrement1 + 1)
                    array[index2].compareAndSet(this, valueBeforeIncrement2 + 1)
                }
                UNDECIDED -> {
                    if (array[index1].value != this && !array[index1].compareAndSet(valueBeforeIncrement1, this) ||
                            array[index2].value != this && !array[index2].compareAndSet(valueBeforeIncrement2, this)) {
                        status.compareAndSet(UNDECIDED, FAILED)
                        array[index1].compareAndSet(this, valueBeforeIncrement1)
                        array[index2].compareAndSet(this, valueBeforeIncrement2)
                    } else if (status.compareAndSet(UNDECIDED, SUCCESS)) {
                        array[index1].compareAndSet(this, valueBeforeIncrement1 + 1)
                        array[index2].compareAndSet(this, valueBeforeIncrement2 + 1)
                    }
                }
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