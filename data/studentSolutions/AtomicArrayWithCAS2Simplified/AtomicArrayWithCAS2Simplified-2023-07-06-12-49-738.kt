//package day3

import AtomicArrayWithCAS2Simplified.Status.*
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
        if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            if (value.index1 == index && value.status.value != SUCCESS) return value.expected1 as E
            if (value.index1 == index && value.status.value == SUCCESS) return value.update1 as E
            if (value.index2 == index && value.status.value != SUCCESS) return value.expected2 as E
            if (value.index2 == index && value.status.value == SUCCESS) return value.update2 as E
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
        if (array[index1].value != expected1 || array[index2].value != expected2) return false
        val (firstIndex, secondIndex) = if (index1 > index2) index1 to index2 else index2 to index1
        val (firstIndexExpected, firstIndexUpdated) = if (firstIndex == index1) expected1 to update1 else expected2 to update2
        val (secondIndexExpected, secondIndexUpdated) = if (secondIndex == index1) expected1 to update1 else expected2 to update2
        val caS2Descriptor = CAS2Descriptor(
            firstIndex, firstIndexExpected, firstIndexUpdated,
            secondIndex, secondIndexExpected, secondIndexUpdated
        )
        caS2Descriptor.apply()
        return caS2Descriptor.status.value == SUCCESS
    }

    private

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
//            val atomicRef1 = array[index1]
            while (!array[index1].compareAndSet(expected1, this)) {
                val value = array[index1].value
                if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    value.apply()
                }
                if (value != expected1) {
                    status.compareAndSet(UNDECIDED, FAILED)
                    return
                }
            }
//            val atomicRef2 = array[index2]
            while (!array[index2].compareAndSet(expected2, this)) {
                val value = array[index2].value
                if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    value.apply()
                }
                if (value != expected2) {
                    // Rollback
                    array[index2].compareAndSet(this, expected1)
                    status.compareAndSet(UNDECIDED, FAILED)
                    return
                }
            }
            status.compareAndSet(UNDECIDED, SUCCESS)
            array[index1].compareAndSet(this, update1)
            array[index2].compareAndSet(this, update2)
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}