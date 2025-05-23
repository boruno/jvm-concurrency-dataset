//package day3

import AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.*
import kotlin.math.max
import kotlin.math.min


// This implementation never stores `null` values.
class AtomicArrayWithCAS2Simplified<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E {
        // TODO: the cell can store CAS2Descriptor
        val value = array[index].value
        if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            return value.getValue(index) as E
        }
        return value as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = CAS2Descriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2, update2 = update2
        )
        descriptor.apply()
        return descriptor.status.value === SUCCESS
    }

    inner class CAS2Descriptor(
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E,
        private val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun getValue(index: Int): E {
            return if (status.value == SUCCESS) {
                if (index == index1) update1 else update2
            } else {
                if (index == index1) expected1 else expected2
            }
        }

        private fun receiveHelp(index: Int) {

            val indexToHelp = if (index == index1) index2 else index1

            val expected = if (indexToHelp == index1) expected1 else expected2

            when(status.value){
                UNDECIDED -> {
                    if (array[indexToHelp].compareAndSet(expected, this) || array[indexToHelp].value == this || status.value == SUCCESS) {
                        updateStatus(SUCCESS)
                    } else {
                        updateStatus(FAILED)
                    }
                }
                SUCCESS -> setValues()
                FAILED -> {
                    array[index1].compareAndSet(this, expected1)
                    array[index2].compareAndSet(this, expected2)
                }
            }
        }


        private fun install(): Boolean {
            val minIndex = min(index1, index2)
            val maxIndex = max(index1, index2)

            val minExpected = if (minIndex == index1) expected1 else expected2
            val maxExpected = if (minIndex == index1) expected2 else expected1

            while (true) {
                when (val firstValue = array[minIndex].value) {
                    minExpected -> {
                        if (array[minIndex].compareAndSet(firstValue, this)) break
                    }
                    is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
                        firstValue.receiveHelp(minIndex)
                    }
                    else -> return false
                }
            }

            while (true) {
                when (val secondValue = array[maxIndex].value) {
                    maxExpected -> {
                        if (array[maxIndex].compareAndSet(secondValue, this)) return true
                    }
                    is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
                        secondValue.receiveHelp(maxIndex)
                    }
                    else -> {
                        if (status.value == UNDECIDED) {
                            //array[minIndex].compareAndSet(this, minExpected)
                            cleanupFirst()
                        }
                        return false
                    }
                }
            }
        }

        private fun updateStatus(newStatus: Status): Boolean {
            return status.compareAndSet(UNDECIDED, newStatus)
        }

        private fun setValues() {
            array[index1].compareAndSet(this, update1)
            array[index2].compareAndSet(this, update2)
        }

        private fun cleanupFirst() {
            val minIndex = min(index1, index2);
            val expected = if (minIndex == index1) expected1 else expected2;
            array[minIndex].compareAndSet(this, expected)
        }

        fun apply() {
            val installed = install()
            if (installed) {
                if (updateStatus(SUCCESS)) {
                    setValues()
                }
            } else {
                updateStatus(FAILED)
            }
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}