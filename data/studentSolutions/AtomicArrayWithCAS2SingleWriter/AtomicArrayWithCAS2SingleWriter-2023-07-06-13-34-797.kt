//package day3

import AtomicArrayWithCAS2SingleWriter.Status.SUCCESS
import AtomicArrayWithCAS2SingleWriter.Status.UNDECIDED
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

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
        val v = array[index].value
        return if (v is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            if (v.status.value == Status.SUCCESS) {
                v.getExpected(index) as E
            } else {
                v.getUpdated(index) as E
            }
        } else {
            v as E
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        if (!array[index1].compareAndSet(expected1, descriptor)) {
            return false
        }
        if (!array[index2].compareAndSet(expected2, descriptor)) {
            descriptor.status.value = Status.FAILED
            array[index1].compareAndSet(descriptor, expected1)
            return false
        }
        descriptor.status.value = SUCCESS
        array[index1].compareAndSet(descriptor, update1)
        array[index2].compareAndSet(descriptor, update2)
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

        fun getExpected(idx: Int): E {
            return if (index1 == idx) expected1
            else if (index2 == idx) expected2
            else throw IllegalArgumentException("$idx")
        }

        fun getUpdated(idx: Int): E {
            return if (index1 == idx) update1
            else if (index2 == idx) update2
            else throw IllegalArgumentException("$idx")
        }

    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}