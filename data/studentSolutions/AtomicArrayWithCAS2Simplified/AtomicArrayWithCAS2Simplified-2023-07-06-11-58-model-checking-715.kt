package day3

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
        val curVal = array[index].value
        val descriptor = curVal as? CAS2Descriptor<E> ?: return curVal as E
        return when (descriptor.status.value) {
            UNDECIDED -> if (descriptor.index1 == index) descriptor.expected1 else descriptor.expected2
            FAILED -> if (descriptor.index1 == index) descriptor.expected1 else descriptor.expected2
            SUCCESS -> if (descriptor.index1 == index) descriptor.update1 else descriptor.update2
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor =
            AtomicArrayWithCAS2SingleWriter.CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        if (!array[index1].compareAndSet(expected1, descriptor)) return false
        if (!array[index2].compareAndSet(expected2, descriptor)) {
            descriptor.status.compareAndSet(
                AtomicArrayWithCAS2SingleWriter.Status.UNDECIDED,
                AtomicArrayWithCAS2SingleWriter.Status.FAILED
            )
            array[index1].value = expected1
            return false
        }
        descriptor.status.compareAndSet(
            AtomicArrayWithCAS2SingleWriter.Status.UNDECIDED,
            AtomicArrayWithCAS2SingleWriter.Status.SUCCESS
        )
        array[index1].value = descriptor.update1
        array[index2].value = descriptor.update2
        return true
    }

    class CAS2Descriptor<E>(
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