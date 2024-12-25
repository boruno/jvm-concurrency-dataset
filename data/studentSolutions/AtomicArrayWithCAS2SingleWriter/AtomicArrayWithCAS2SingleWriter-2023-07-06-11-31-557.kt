//package day3

import day3.AtomicArrayWithCAS2SingleWriter.Status.*
import kotlinx.atomicfu.*
import java.lang.ClassCastException

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
        while (true) {
            val value = array[index].value
            if (value is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
                if (value.status.value == SUCCESS) return value.get(index) as E
                continue
            }
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
            if (array[index2].compareAndSet(expected2, descriptor)) {
                descriptor.status.value = SUCCESS
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
                return true
            } else {
                descriptor.status.value = FAILED
            }
        }
        return false

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

        fun get(index: Int): E {
            return if (index == index1) update1 else update2
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}
