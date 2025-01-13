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
            while (status.value == UNDECIDED) {
                val v1 = array[index1].value
                if (v1 != this) {
                    if (v1 is IncrementDescriptor) {
                        v1.applyOperation()
                        continue
                    } else if (v1 != valueBeforeIncrement1) {
                        status.compareAndSet(UNDECIDED, FAILED)
                        continue
                    } else if (!array[index1].compareAndSet(valueBeforeIncrement1, this)) {
                        continue
                    }
                }
                val v2 = array[index2].value
                if (v2 != this) {
                    if (v2 is IncrementDescriptor) {
                        v2.applyOperation()
                        continue
                    } else if (v2 != valueBeforeIncrement2) {
                        status.compareAndSet(UNDECIDED, FAILED)
                        continue
                    } else if (!array[index2].compareAndSet(valueBeforeIncrement2, this)) {
                        continue
                    }
                }
                status.compareAndSet(UNDECIDED, SUCCESS)
            }
            when (status.value) {
                FAILED -> {
                    array[index1].compareAndSet(this, valueBeforeIncrement1)
                    array[index2].compareAndSet(this, valueBeforeIncrement2)
                }
                SUCCESS -> {
                    array[index1].compareAndSet(this, valueBeforeIncrement1 + 1)
                    array[index2].compareAndSet(this, valueBeforeIncrement2 + 1)
                }
                else -> throw IllegalStateException("Can't happen")
            }
        }
    }

    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}