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
        // TODO: the cell can store CAS2Descriptor
        val value = array[index].value
        if (value !is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor)
            return value as E

        val status = value.status.value
        if (status == UNDECIDED || status == FAILED)
            return if (value.index1 == index) value.expected1 as E else value.expected2 as E

        value.apply()

        return if (value.index1 == index) value.update1 as E else value.update2 as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: use CAS2Descriptor to fix it.

        if (array[index1].value != expected1 || array[index2].value != expected2) return false

        val caS2Descriptor = CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        if (!array[index1].compareAndSet(expected1, caS2Descriptor))
            return false

        if (!array[index2].compareAndSet(expected2, caS2Descriptor)) {
            caS2Descriptor.status.compareAndSet(UNDECIDED, FAILED)
            caS2Descriptor.apply()
            return false
        }

        caS2Descriptor.status.compareAndSet(UNDECIDED, SUCCESS)
        caS2Descriptor.apply()
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
            val localStatus = status.value
            // TODO: install the descriptor, update the status, update the cells.
            if (localStatus == SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
                return
            }

            if (localStatus == FAILED) {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
                return
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}