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
        val res = when (val cell = array[index].value) {
            is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor -> cell.getValue(index)
            else -> (cell ?: throw Exception(""))
        }
        return res as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor =
            if (index1 < index2) {
                CAS2Descriptor(
                    index1 = index1, expected1 = expected1, update1 = update1,
                    index2 = index2, expected2 = expected2, update2 = update2
                )
            }
            else {
                CAS2Descriptor(
                    index1 = index2, expected1 = expected2, update1 = update2,
                    index2 = index1, expected2 = expected1, update2 = update1
                )
            }
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

        private fun helpAndGet(idx: Int): Any? {
            var cell = array[idx].value
            while (cell is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                cell.apply()
                cell = array[idx].value
            }
            return cell
        }

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            val old1 = helpAndGet(index1)
            val old2 = helpAndGet(index2)
            val succeeded1 = installDescriptor(index1, expected1)
            val succeeded2 = installDescriptor(index2, expected2)
            updateStatus(succeeded1, succeeded2)
            updateCells(old1, old2)
        }

        private fun installDescriptor(idx: Int, exp: E): Boolean {
            var cell = array[idx].value
            while (cell is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                cell.apply()
                cell = array[idx].value
            }
            return array[idx].compareAndSet(exp, this)
        }

        private fun updateStatus(succeeded1: Boolean, succeeded2: Boolean) {
            if (succeeded1 && succeeded2) status.compareAndSet(UNDECIDED, SUCCESS)
            else status.compareAndSet(UNDECIDED, FAILED)
        }

        private fun updateCells(old1: Any?, old2: Any?) {
            if (status.value == SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            } else {
                array[index1].compareAndSet(this, old1)
                array[index2].compareAndSet(this, old2)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}