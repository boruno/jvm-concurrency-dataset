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

        val value = array[index].value

        return when (value) {
            is Int -> value
            is IncrementDescriptor -> value.getValue(index)
            else -> throw Exception("Wrong type")
        }
    }

    fun inc2(index1: Int, index2: Int) {
        require(index1 != index2) { "The indices should be different" }
        // TODO: This implementation is not linearizable!
        // TODO: Use `IncrementDescriptor` to perform the operation atomically.

        val indexLeft = if (index1 < index2) index1 else index2
        val indexRight = if (index1 > index2) index1 else index2

        while (true) {
            val leftValue = get(indexLeft)
            val rightValue = get(indexRight)
            val descriptor = IncrementDescriptor(
                indexLeft,
                leftValue,
                indexRight,
                rightValue,
            )
            if (descriptor.applyOperation())
                return
        }


        //        array[index1].value = array[index1].value!! + 1
//        array[index2].value = array[index2].value!! + 1
    }


//        array[index1].value = array[index1].value!! + 1
//        array[index2].value = array[index2].value!! + 1
//}

    // TODO: Implement the `inc2` operation using this descriptor.
// TODO: 1) Read the current cell states
// TODO: 2) Create a new descriptor
// TODO: 3) Call `applyOperation()` -- it should try to increment the counters atomically.
// TODO: 4) Check whether the `status` is `SUCCESS` or `FAILED`, restarting in the latter case.
    private inner class IncrementDescriptor(
        val indexLeft: Int, val valueBeforeIncrementLeft: Int,
        val indexRight: Int, val valueBeforeIncrementRight: Int
    ) {
        val incrementedLeft = valueBeforeIncrementLeft + 1
        val incrementedRight = valueBeforeIncrementRight + 1

        val status = atomic(UNDECIDED)

        // TODO: Other threads can call this function
        // TODO: to help completing the operation.

        fun getValue(index: Int): Int {
            return when (status.value) {
                UNDECIDED -> if (indexLeft == index) valueBeforeIncrementLeft else if (indexRight == index) valueBeforeIncrementRight else throw Exception(
                    "Doh"
                )

                FAILED -> if (indexLeft == index) valueBeforeIncrementLeft else if (indexRight == index) valueBeforeIncrementRight else throw Exception(
                    "Doh"
                )

                SUCCESS -> if (indexLeft == index) incrementedLeft else if (indexRight == index) incrementedRight else throw Exception(
                    "Doh"
                )
            }
        }

        fun applyOperation(): Boolean {

            while (true) {
                when (status.value) {
                    UNDECIDED -> {

                        if (install(indexLeft, valueBeforeIncrementLeft) &&
                            install(indexRight, valueBeforeIncrementRight) &&
                            status.compareAndSet(UNDECIDED, SUCCESS)
                        ) {
                            continue
                        } else {
                            status.value = FAILED
                            continue
                        }
                    }

                    FAILED -> {
                        uninstall(indexLeft, valueBeforeIncrementLeft)
                        uninstall(indexRight, valueBeforeIncrementRight)
                        return false
                    }

                    SUCCESS -> {
                        uninstall(indexLeft, incrementedLeft)
                        uninstall(indexRight, incrementedRight)
                        return true
                    }
                }
            }


            // TODO: Use the CAS2 algorithm, installing this descriptor
            // TODO: in `array[index1]` and `array[index2]` cells.
        }

        private fun install(index: Int, valueBeforeIncrement: Int): Boolean {
            val cell = array[index]
            while (true) {
                if (!cell.compareAndSet(valueBeforeIncrement, this)) {
                    val cellValue = cell.value
                    if (cellValue is Int) {
                        return false
                    } else if (cellValue != this && cellValue is IncrementDescriptor) {
//                        cellValue.applyOperation()
//                        continue
                        return false
                    }
                    // els it's me, let's continue
                    return true
                }
                return true
            }
        }

        private fun uninstall(index: Int, value: Int) {
            array[index].compareAndSet(this, value)
        }

    }

    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}