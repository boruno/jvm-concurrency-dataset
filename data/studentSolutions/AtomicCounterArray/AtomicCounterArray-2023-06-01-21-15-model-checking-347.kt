@file:Suppress("DuplicatedCode")

package day4

import day4.AtomicCounterArray.Status.*
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
        val leftIndex = min(index1, index2)
        val rightIndex = max(index1, index2)
        while (true) {
            val descriptor = IncrementDescriptor(
                leftIndex, get(leftIndex),
                rightIndex, get(rightIndex)
            )
            descriptor.applyOperation()
            if (descriptor.status.value === SUCCESS) return
        }
    }

    private inner class IncrementDescriptor(
        val index1: Int, val valueBeforeIncrement1: Int,
        val index2: Int, val valueBeforeIncrement2: Int
    ) {
        val status = atomic(UNDECIDED)

        fun applyOperation() {
            while (status.value === UNDECIDED) {
                if (!anchorDescriptor(index1, valueBeforeIncrement1)) continue
                if (!anchorDescriptor(index2, valueBeforeIncrement2)) continue
                status.compareAndSet(UNDECIDED, SUCCESS)
            }
            when (status.value) {
                FAILED -> revertOperation()
                SUCCESS -> completeOperation()
                else -> throw IllegalStateException("descriptor status can't be UNDECIDED when operation is finishing")
            }
        }

        private fun anchorDescriptor(index: Int, valueBeforeIncrement: Int): Boolean {
            array[index].compareAndSet(valueBeforeIncrement, this)
            val element = array[index].value
            when {
                element === this -> return true
                element is IncrementDescriptor -> element.applyOperation()
                element is Int -> status.compareAndSet(UNDECIDED, FAILED)
            }
            return false
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