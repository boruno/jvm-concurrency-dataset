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
        val ref = array[index]
        val value = ref.value
        if (value is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            if (index == value.index1) return if (value.status.value == AtomicArrayWithCAS2SingleWriter.Status.SUCCESS) value.update1 as E else value.expected1 as E
            if (index == value.index2) return if (value.status.value == AtomicArrayWithCAS2SingleWriter.Status.SUCCESS) value.update2 as E else value.expected2 as E
        }
        return value as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        val ref1 = array[index1]
        if (!ref1.compareAndSet(expected1, descriptor)) {
            descriptor.status.value = FAILED
            return false
        }
        val ref2 = array[index2]
        if (!ref2.compareAndSet(expected2, descriptor)) {
            descriptor.status.value = FAILED
            ref1.compareAndSet(descriptor, expected1)
            return false
        }
        descriptor.status.value = SUCCESS
        ref1.compareAndSet(descriptor, update1)
        ref2.compareAndSet(descriptor, update2)
        return true
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

        fun apply() {
            // TODO: install the descriptor, update the status, update the cells.
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}