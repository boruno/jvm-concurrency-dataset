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
            val ref = array[index]
            val value = ref.value
        if (value is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            if (index == value.index1) return value.expected1 as E
            if (index == value.index2) return value.expected2 as E
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
        val descriptor = CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        val ref1 = array[index1]
        if (!ref1.compareAndSet(expected1, descriptor)) {
            return false
        }
        val ref2 = array[index2]
        if (!ref2.compareAndSet(expected2, descriptor)) {
            ref1.compareAndSet(descriptor, expected1)
            return false
        }
        ref1.compareAndSet(descriptor, update1)
        ref2.compareAndSet(descriptor, update2)
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