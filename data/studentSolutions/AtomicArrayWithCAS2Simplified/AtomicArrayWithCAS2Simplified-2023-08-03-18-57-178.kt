//package day3

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
            is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor ->
                cell.getValue(index)
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

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            // TODO: In this task, only one thread can call cas2(..),
            // TODO: so cas2(..) calls cannot be executed concurrently.
            if (status.value == UNDECIDED) {
                val installed = installDescriptor()
                updateStatus(installed)
            }
            updateCells()
        }

        private fun installDescriptor(): Boolean {
            var installed1 = false
            var installed2 = false

            while (true) {
                val cell1 = array[index1].value
                if (!installed1) {
                    if (cell1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                        if (cell1 != this) {
                            cell1.apply()
                            continue
                        }
                        else installed1 = true
                    }
                    val x = array[index1].compareAndSet(expected1, this)
                    installed1 = installed1 || x
                    if (!installed1) return false
                }

                val cell2 = array[index2].value
                if (!installed2 && cell2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    if (cell2 != this) {
                        cell2.apply()
                        continue
                    }
                    else installed2 = true
                }
                installed2 = installed2 || array[index2].compareAndSet(expected2, this)
                return installed2
            }
        }

        private fun updateStatus(installed: Boolean) {
            val newStatus =  if (installed) SUCCESS else FAILED
            status.compareAndSet(UNDECIDED, newStatus)
        }

        private fun updateCells() {
            if (status.value == SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
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