//package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.*
import kotlin.math.max
import kotlin.math.min


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
        val curValue = array[index].value
        return (if (curValue is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor)
            curValue.value(index) else curValue) as E
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
            install()
            updateStatus()
            updatePhysically()
        }

        fun value(index: Int): E {
            return if (status.value == SUCCESS) {
                if (index == index1) update1 else update2
            } else {
                if (index == index1) expected1 else expected2
            }
        }

        private fun install(): Boolean {
            val idx1 = min(index1, index2)
            val idx2 = max(index1, index2)
            while (true) {
                if (array[idx1].compareAndSet(expected1, this)) {
                    if (array[idx2].compareAndSet(expected2, this)) {
                        val curValue = array[idx2].value
                        if (curValue is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor && curValue != this) curValue.apply()
                    }
                } else {
                    val curValue = array[idx1].value
                    if (curValue is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor && curValue != this) curValue.apply()
                }
            }
        }

        private fun updateStatus() {
            if (array[index1].value == this && array[index2].value == this) {
                status.value = SUCCESS
            } else {
                status.value = FAILED
            }
        }

        private fun updatePhysically() {
            if (status.value == SUCCESS) {
                array[index1].value = update1
                array[index2].value = update2
            } else {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}