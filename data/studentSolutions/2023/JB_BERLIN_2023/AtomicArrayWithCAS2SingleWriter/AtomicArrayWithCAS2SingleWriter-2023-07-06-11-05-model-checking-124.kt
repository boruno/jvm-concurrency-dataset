package day3

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
        return when (val value = array[index].value) {
            is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor -> {
                if (value.status.value == SUCCESS) return value.update1 as E
                else return value.expected1 as E
            }
            else -> {
                value as E
            }
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        if(index1 == index2) return false
        // TODO: this implementation is not linearizable,
        // TODO: use CAS2Descriptor to fix it.
        // TODO: Note that only one thread can call CAS2!
        val descriptor = CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        if (!array[index1].compareAndSet(expected1, descriptor)) return false
        if (!array[index2].compareAndSet(expected2, update2)) {
            array[index1].compareAndSet(descriptor, expected1)
        } else {
            descriptor.status.compareAndSet(UNDECIDED, SUCCESS)
            array[index1].compareAndSet(expected1, update1)
        }
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
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}