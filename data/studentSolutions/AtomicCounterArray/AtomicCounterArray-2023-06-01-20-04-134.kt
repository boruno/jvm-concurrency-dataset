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
        return when(val value = array[index].value) {
            is Int -> value
            is IncrementDescriptor -> value.getValue(index)
            else -> throw IllegalStateException("Unknown value type ${value?.javaClass}")
        }
    }

    fun inc2(index1: Int, index2: Int) {
        require(index1 != index2) { "The indices should be different" }
        while (true) {
            val descriptor = IncrementDescriptor(
                index1, get(index1),
                index2, get(index2)
            )
            descriptor.applyOperation()
            if (descriptor.status.value == SUCCESS) return
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

        fun applyOperation() {
            if (array[index1].compareAndSet(valueBeforeIncrement1, this)) {
                if (array[index2].compareAndSet(valueBeforeIncrement2, this)) {
                    if (status.compareAndSet(UNDECIDED, SUCCESS)) {
                        array[index1].compareAndSet(this, valueBeforeIncrement1 + 1)
                        array[index2].compareAndSet(this, valueBeforeIncrement2 + 1)
                        return
                    }
                }
            }
            status.compareAndSet(UNDECIDED, FAILED)
        }

        fun getValue(index: Int): Int {
            return when(index) {
                index1 -> if (status.value == SUCCESS) valueBeforeIncrement1 + 1 else valueBeforeIncrement1
                index2 -> if (status.value == SUCCESS) valueBeforeIncrement2 + 1 else valueBeforeIncrement2
                else -> throw IllegalStateException("$this has no value for index $index")
            }
        }

        override fun toString() = "Descriptor[$index1, $index2]"
    }

    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}
