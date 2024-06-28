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
        // TODO: the cell can store a descriptor.
        val inCell = array[index].value
        if (inCell is Int) return inCell
        if (inCell is IncrementDescriptor) return inCell.get(index)
        return 0
    }

    fun inc2(index1: Int, index2: Int) {
        require(index1 != index2) { "The indices should be different" }
        // TODO: This implementation is not linearizable!
        // TODO: Use `IncrementDescriptor` to perform the operation atomically.
        if (index1 == index2) return
        val ind1 = minOf(index1, index2)
        val ind2 = maxOf(index1, index2)
        while (true) {
            val pic1 = get(ind1)
            val pic2 = get(ind2)
            val descriptor = IncrementDescriptor(
                ind1, pic1,
                ind2, pic2
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
        val status = atomic(UNDECIDED)

        // TODO: Other threads can call this function
        // TODO: to help completing the operation.
        fun applyOperation() {
            // TODO: Use the CAS2 algorithm, installing this descriptor
            // TODO: in `array[index1]` and `array[index2]` cells.
            while (status.value == UNDECIDED) {
                if (put1() && put2()) status.compareAndSet(UNDECIDED, SUCCESS)
            }
            apply1()
            apply2()
        }

        fun put1(): Boolean {
            while (true) {
                var inCell = array[index1].value
                if (inCell is Int) {
                    if (inCell == valueBeforeIncrement1) {
                        if (array[index1].compareAndSet(inCell, this)) {
                            return true
                        } else {
                            continue
                        }
                    } else {
                        status.compareAndSet(UNDECIDED, FAILED)
                        return false
                    }
                }
                if (inCell is IncrementDescriptor) {
                    if (inCell == this) return true
                    val currValue = inCell.get(index1)
                    if (currValue != valueBeforeIncrement1) {
                        status.compareAndSet(UNDECIDED, FAILED)
                        return false
                    }
                    inCell.applyOperation()
                    continue
                }
            }
        }
        fun put2(): Boolean {
            while (true) {
                val inCell = array[index2].value
                if (inCell is Int) {
                    if (inCell == valueBeforeIncrement2) {
                        if (array[index2].compareAndSet(inCell, this)) {
                            return true
                        } else {
                            continue
                        }
                    } else {
                        status.compareAndSet(UNDECIDED, FAILED)
                        return false
                    }
                }
                if (inCell is IncrementDescriptor) {
                    if (inCell == this) return true
                    val currValue = inCell.get(index2)
                    if (currValue != valueBeforeIncrement2) {
                        status.compareAndSet(UNDECIDED, FAILED)
                        return false
                    }
                    inCell.applyOperation()
                    continue
                }
            }
        }

        fun apply1() {
            val newVal: Any = when (status.value) {
                UNDECIDED -> this
                SUCCESS -> valueBeforeIncrement1 + 1
                FAILED -> valueBeforeIncrement1
            }
            array[index1].compareAndSet(this, newVal)
        }
        fun apply2() {
            val newVal: Any = when (status.value) {
                UNDECIDED -> this
                SUCCESS -> valueBeforeIncrement2 + 1
                FAILED -> valueBeforeIncrement2
            }
            array[index2].compareAndSet(this, newVal)
        }

        fun getStatus(): Status {
            if (status.value == UNDECIDED) {
                if (put1() && put2()) status.compareAndSet(UNDECIDED, SUCCESS)
                else status.compareAndSet(UNDECIDED, FAILED)
            }
            return status.value
        }

        fun get(index: Int) : Int {
            val currStatus = status.value
            val incr = if (getStatus() == SUCCESS) 1 else 0
            val before = if (index == index1) valueBeforeIncrement1 else valueBeforeIncrement2
            return before + incr
        }

        fun undecided() {

        }
    }

    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}