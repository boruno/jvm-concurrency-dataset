//package day3

import AtomicArrayWithCAS2SingleWriter.Status.*
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
        val value = array[index].value
        if (value is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            val cas = value as AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor
            if (cas.status.value == SUCCESS) {
                if (index == cas.index1) {return cas.update1 as E}
                else {return cas.update2 as E}
            } else {
                if (index == cas.index1) {return cas.expected1 as E}
                else {return cas.expected2 as E}
            }
        } else {
            return value as E
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: use CAS2Descriptor to fix it.
        // TODO: Note that only one thread can call CAS2!

        val descriptor = CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)

        if (array[index1].compareAndSet(expected1, descriptor)) {
            array[index2].compareAndSet(expected2, descriptor)
            descriptor.status.compareAndSet(UNDECIDED, SUCCESS)
        } else {
            return false
        }

        array[index2].compareAndSet(descriptor, update2)
        array[index1].compareAndSet(descriptor, update1)
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