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
        val cellState = array[index].value
        if (cellState is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            if (cellState.status.value == AtomicArrayWithCAS2SingleWriter.Status.SUCCESS) {
                if (cellState.index1 == index) return cellState.update1 as E
                if (cellState.index2 == index) return cellState.update2 as E
            }
            else
            {
                if (cellState.index1 == index) return cellState.expected1 as E
                if (cellState.index2 == index) return cellState.expected2 as E
            }
        }
        return cellState as E
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
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E,
        private val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            val elem1 = array[index1].value
            if (elem1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                if (elem1.status.value != UNDECIDED) applyStatus()
            } else {
                if (array[index1].value != expected1) status.compareAndSet(UNDECIDED, FAILED)
                else array[index1].value = this
            }

            val elem2 = array[index2].value
            if (elem2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                if (elem2.status.value != UNDECIDED) applyStatus()
            } else {
                if (array[index2].value != expected2) status.compareAndSet(UNDECIDED, FAILED)
                else array[index2].value = this
            }

            status.compareAndSet(UNDECIDED, SUCCESS)

            if (status.value === SUCCESS) applySuccess()
            if (status.value == FAILED) applyFailed()
        }

        fun applyStatus()
        {
            if (status.value == SUCCESS) applySuccess()
            if (status.value == FAILED) applyFailed()
        }

        fun applySuccess() {
            val elem1 = array[index1].value
            if (elem1 is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor)
                array[index1].compareAndSet(elem1, update1)

            val elem2 = array[index2].value
            if (elem2 is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor)
                array[index2].compareAndSet(elem2, update2)
        }

        fun applyFailed()
        {
            val elem1 = array[index1].value
            if (elem1 is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor)
                array[index1].compareAndSet(elem1, expected1)

            val elem2 = array[index2].value
            if (elem2 is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor)
                array[index2].compareAndSet(elem2, expected2)
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}