//package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.*


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
            if (value.index1 == index && value.status.value != SUCCESS) return value.expected1 as E
            if (value.index1 == index && value.status.value == SUCCESS) return value.update1 as E
            if (value.index2 == index && value.status.value != SUCCESS) return value.expected2 as E
            if (value.index2 == index && value.status.value == SUCCESS) return value.update2 as E
        }
        return value as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: use CAS2Descriptor to fix it.
//        if (array[index1].value != expected1 || array[index2].value != expected2) return false

        // Make ordering for indexies
        val caS2Descriptor = if (index1 < index2) {
            CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        } else {
            CAS2Descriptor(index2, expected2, update2, index1, expected1, update1)
        }
        caS2Descriptor.apply()
        return caS2Descriptor.status.value === SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            // TODO: install the descriptor, update the status, update the cells.
            val result = tryToSetValue(index1, expected1) && tryToSetValue(index2, expected2)
            if (result) {
                status.compareAndSet(UNDECIDED, SUCCESS)
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }
            updateValues()
        }

        private fun updateValues() {
            if (status.value == SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            } else {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
            }
        }

        private fun tryToSetValue(index: Int, expected: E): Boolean {
            while (!array[index].compareAndSet(expected, this)) {
                val value = array[index].value
                if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    if (value === this) return true
                    value.apply()
                }
                if (value != expected) {
                    return false
                }
            }
            return true
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}