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

        val descriptor: AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor?
        val value = array[index].value
        if (value is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            descriptor = value
        } else {
            return value as E
        }

        val status = descriptor.status.value
        if (index == descriptor.index1) {
            if (status == SUCCESS) return descriptor.update1 as E
            if (status == UNDECIDED || status == FAILED) return descriptor.expected1 as E
        }

        if (index == descriptor.index2) {
            if (status == SUCCESS) return descriptor.update2 as E
            if (status == UNDECIDED || status == FAILED) return descriptor.expected2 as E
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

        val descriptor = CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        return descriptor.apply()
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

        fun apply(): Boolean {
            // TODO: install the descriptor, update the status, update the cells.

            if (array[index1].compareAndSet(expected1, this)) {
                if (array[index2].compareAndSet(expected2, this)) {
                    status.compareAndSet(UNDECIDED, SUCCESS)
                    array[index1].compareAndSet(this, update1)
                    array[index2].compareAndSet(this, update2)
                    return true
                } else {
                    status.compareAndSet(UNDECIDED, FAILED)
                    array[index1].compareAndSet(this, expected1)
                    return false
                }
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
                return false
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}