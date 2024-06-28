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
        when(val element = array[index].value) {
            is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor -> {
                when(element.status.value) {
                    AtomicArrayWithCAS2SingleWriter.Status.UNDECIDED, AtomicArrayWithCAS2SingleWriter.Status.FAILED -> {
                        if (index == element.index1) {
                            return element.expected1 as E
                        } else {
                            return element.expected2 as E
                        }
                    }
                    AtomicArrayWithCAS2SingleWriter.Status.SUCCESS ->{
                        if (index == element.index1) {
                            return element.update1 as E
                        } else {
                            return element.update2 as E
                        }
                    }
                }
            }
            else -> return element as E
        }
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
            val status = install()
            updateStatus(status)
            update()
        }

        private fun updateStatus(newStatus: Status) {
            status.compareAndSet(UNDECIDED, newStatus)
        }
        private fun install(): Status {
            if(array[index1].compareAndSet(expected1, this) && array[index2].compareAndSet(expected2, this)) {
                return SUCCESS
            } else {
                return FAILED
            }
        }

        private fun update() {
            when(status.value) {
                Status.UNDECIDED, Status.FAILED -> {
                    array[index1].compareAndSet(this, expected1)
                    array[index2].compareAndSet(this, expected2)
                }
                Status.SUCCESS -> {
                    array[index1].compareAndSet(this, update1)
                    array[index2].compareAndSet(this, update2)
                }
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}