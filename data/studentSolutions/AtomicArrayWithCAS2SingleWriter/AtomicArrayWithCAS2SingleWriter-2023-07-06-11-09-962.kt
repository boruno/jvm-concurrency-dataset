//package day3

import day3.AtomicArrayWithCAS2SingleWriter.Status.*
import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class AtomicArrayWithCAS2SingleWriter<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E {
        // TODO: the cell can store CAS2Descriptor

        val descriptor: AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor?
        val value = array[index].value
        if (value is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            descriptor = value
        } else {
            return value as E
        }

        if (index == descriptor.index1) {
            if (descriptor.status.value == SUCCESS) return descriptor.update1 as E
            if (descriptor.status.value == UNDECIDED || descriptor.status.value == FAILED) return descriptor.expected1 as E
        }

        if (index == descriptor.index2) {
            if (descriptor.status.value == SUCCESS) return descriptor.update2 as E
            if (descriptor.status.value == UNDECIDED || descriptor.status.value == FAILED) return descriptor.expected2 as E
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
        // TODO: Note that only one thread can call CAS2!
        if (array[index1].value != expected1 || array[index2].value != expected2) return false
        array[index1].value = update1
        array[index2].value = update2
        return true
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

            val index1Success = array[index1].compareAndSet(expected1, this)
            val index2Success = array[index2].compareAndSet(expected2, this)

            if (index1Success && index2Success) {
                status.value = SUCCESS
            } else {
                status.value = FAILED
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}