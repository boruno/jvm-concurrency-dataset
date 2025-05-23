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
        while(true) {
            val curr = array[index].value!!
            if (curr is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor
                && (curr.status.value == FAILED || curr.status.value == UNDECIDED)){
                return if (index == curr.index1)
                    curr.expected1 as E
                else
                    curr.expected2  as E
            } else if (curr is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor
                && curr.status.value == SUCCESS) {
                return if (index == curr.index1)
                    curr.update1 as E
                else
                    curr.update2  as E
            }
        }
        // TODO: the cell can store CAS2Descriptor
        return array[index].value as E
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
         val index1: Int,
         val expected1: E,
         val update1: E,
         val index2: Int,
         val expected2: E,
         val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            if (array[index1].compareAndSet(expected1, update1) && array[index2].compareAndSet(expected2, update2)) {
                status.compareAndSet(UNDECIDED, SUCCESS)
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            // TODO: In this task, only one thread can call cas2(..),
            // TODO: so cas2(..) calls cannot be executed concurrently.
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}
