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
        // TODO: the cell can store CAS2Descriptor
        val res: E = when (val cell = array[index].value) {
            is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor -> cell.getValue(index) as E
            else -> (cell ?: throw Exception("")) as E
        }
        return res
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
        fun getValue(idx: Int) : E {
            return if (status.value == SUCCESS)
                        if (idx == index1) update1 else update2
                   else if (idx == index1) expected1 else expected2
        }


        val status = atomic(UNDECIDED)

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            // TODO: In this task, only one thread can call cas2(..),
            // TODO: so cas2(..) calls cannot be executed concurrently.
            val succeeded1 = installDescriptor(index1, expected1)
            val succeeded2 = installDescriptor(index2, expected2)
            updateStatus(succeeded1, succeeded2)
            updateCells(succeeded1)
        }

        private fun installDescriptor(idx: Int, exp: E): Boolean {
            return array[idx].compareAndSet(exp, this)
        }

        private fun updateStatus(succeeded1: Boolean, succeeded2: Boolean) {
            if (succeeded1 && succeeded2) status.compareAndSet(UNDECIDED, SUCCESS)
            else status.compareAndSet(UNDECIDED, FAILED)
        }

        private fun updateCells(succeeded1: Boolean) {
            if (status.value == SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            } else {
                if (succeeded1) { array[index1].compareAndSet(this, expected1) }
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}