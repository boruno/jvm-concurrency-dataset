@file:Suppress("DuplicatedCode")

//package day4

import AtomicCounterArray.Status.*
import kotlinx.atomicfu.*
import java.lang.IllegalStateException
import kotlin.math.max
import kotlin.math.min

// This implementation never stores `null` values.
class AtomicCounterArray(size: Int) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with zeros.
        for (i in 0 until size) {
            array[i].value = 0
        }
    }

    fun get(index: Int): Int {
        when (val element = array[index].value) {
            is Int -> return element
            is IncrementDescriptor -> with (element) {
                return when (index) {
                    index1 -> if (status.value == SUCCESS) valueBeforeIncrement1 + 1 else valueBeforeIncrement1
                    index2 -> if (status.value == SUCCESS) valueBeforeIncrement2 + 1 else valueBeforeIncrement2
                    else -> throw IllegalStateException("inc2 descriptor was placed in the wrong array cell")
                }
            }
            else -> throw IllegalStateException("impossible type of array value")
        }
    }

    fun inc2(index1: Int, index2: Int) {
        require(index1 != index2) { "The indices should be different" }
        // TODO comment
        val leftIndex = min(index1, index2)
        val rightIndex = max(index1, index2)
        // TODO comment
        while (true) {
            val descriptor = IncrementDescriptor(
                leftIndex, get(leftIndex),
                rightIndex, get(rightIndex)
            )
            descriptor.applyOperation()
            if (descriptor.status.value === SUCCESS) return
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

        fun plantDescriptor(index: Int, valueBeforeIncrement: Int): Boolean {
            if (array[index].compareAndSet(valueBeforeIncrement, this)) return true
            val element = array[index1].value
            when {
                element is IncrementDescriptor && element !== this -> {
                    element.applyOperation()
                    return false
                }
                element is Int && element > valueBeforeIncrement1 -> {
                    status.compareAndSet(UNDECIDED, FAILED)
                    return false
                }
            }
            return true
        }

        fun applyOperation() {
            while (true) {
                when (status.value) {
                    SUCCESS -> {
                        completeOperation()
                        return
                    }
                    FAILED -> {
                        revertOperation()
                        return
                    }
                    else -> {}
                }
                if (!plantDescriptor(index1, valueBeforeIncrement1)) continue
                if (!plantDescriptor(index2, valueBeforeIncrement2)) continue
                status.compareAndSet(UNDECIDED, SUCCESS)
            }
        }

        private fun revertOperation() {
            array[index1].compareAndSet(this, valueBeforeIncrement1)
            array[index2].compareAndSet(this, valueBeforeIncrement2)
        }

        private fun completeOperation() {
            array[index1].compareAndSet(this, valueBeforeIncrement1 + 1)
            array[index2].compareAndSet(this, valueBeforeIncrement2 + 1)
        }
    }

    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}