@file:Suppress("DuplicatedCode")

package day4

import day4.AtomicCounterArray.Status.*
import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class AtomicCounterArray(size: Int) {
    private val array = atomicArrayOfNulls<Any>(size)

    internal fun state(): String = List(array.size) { array[it].value }.joinToString()

    init {
        // Fill array with zeros.
        for (i in 0 until size) {
            array[i].value = 0
        }
    }

    fun get(index: Int): Int {
        val cell = array[index]
        val rawValue = cell.value
        if (rawValue is IncrementDescriptor) {
            val base = when (index) {
                rawValue.index1 -> rawValue.valueBeforeIncrement1
                rawValue.index2 -> rawValue.valueBeforeIncrement2
                else -> error("Bad descriptor")
            }
            return when (rawValue.status.value) {
                UNDECIDED, FAILED -> {
                    // FIXME maybe assist?
                    base
                }
                SUCCESS -> {
                    cell.compareAndSet(rawValue, base + 1)
                    base + 1
                }
            }
        }
        return rawValue as Int
    }

    fun inc2(index1: Int, index2: Int) {
        require(index1 != index2) { "The indices should be different" }
        if (index1 > index2) return inc2(index2, index1)
        require(index1 < index2)
        repeat(1_000_000) {
            val d = IncrementDescriptor(
                index1 = index1,
                valueBeforeIncrement1 = get(index1),
                index2 = index2,
                valueBeforeIncrement2 = get(index2),
            )
            if (d.applyOperation()) return
        }
        error("wai?!")
    }

    private inner class IncrementDescriptor(
        val index1: Int, val valueBeforeIncrement1: Int,
        val index2: Int, val valueBeforeIncrement2: Int
    ) {
        val status = atomic(UNDECIDED)

/*
        inline fun AtomicRef<Any?>.compareAndSetToThis(expectedValue: Any): Boolean {
            val currentValue = value
            if (currentValue === this@IncrementDescriptor) return true
            return compareAndSet(expectedValue, this@IncrementDescriptor)
        }
*/

        fun applyOperation(): Boolean {
            val i1 = index1
            val i2 = index2
            require(i1 < i2)
            val cell1 = array[i1]
            val cell2 = array[i2]

            while (true) {
                val cell1Value = cell1.value
                if (cell1Value === this) break
                if (cell1Value is IncrementDescriptor) {
                    cell1Value.applyOperation()
                    continue
                }
                if (!cell1.compareAndSet(valueBeforeIncrement1, this)) {
                    fail()
                    return status.value == SUCCESS
                }
            }
            while (true) {
                val cell2Value = cell2.value
                if (cell2Value === this) break
                if (cell2Value is IncrementDescriptor) {
                    cell2Value.applyOperation()
                    continue
                }
                if (!cell2.compareAndSet(valueBeforeIncrement2, this)) {
                    fail()
                    return status.value == SUCCESS
                }
            }
            status.compareAndSet(UNDECIDED, SUCCESS)
            if (status.value == SUCCESS) {
                cell1.compareAndSet(this, valueBeforeIncrement1 + 1)
                cell2.compareAndSet(this, valueBeforeIncrement2 + 1)
            }
            return true
        }

        fun fail() {
            val i1 = index1
            val i2 = index2
            val cell1 = array[i1]
            val cell2 = array[i2]

            status.compareAndSet(UNDECIDED, FAILED)
            if (status.value == FAILED) {
                cell1.compareAndSet(this, valueBeforeIncrement1)
                cell2.compareAndSet(this, valueBeforeIncrement2)
            }
        }

        override fun toString(): String =
            "D{i1=$index1 i2=$index2 ; v1=$valueBeforeIncrement1 v2=$valueBeforeIncrement2 ; s=${status.value}}"
    }

    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}