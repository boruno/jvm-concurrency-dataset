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

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        val value = array[index].value
        if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            return value.getValue(index) as E
        }
        return value as E
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

        fun getValue(index: Int): E {
            return if (status.value == SUCCESS) {
                if (index == index1) update1 else update2
            } else {
                if (index == index1) expected1 else update2
            }
        }

        private fun receiveHelp(index: Int) {
            val a = if (index == index1) {
                array[index2].compareAndSet(expected2, this)
            } else {
                array[index1].compareAndSet(expected1, this)
            }

            if (a) {
                updateStatus(SUCCESS)
                setValues()
            }
            else {
                updateStatus(FAILED)
            }
        }

        private fun install(): Boolean {
            if (array[index1].compareAndSet(expected1, this)) {
                return if (array[index2].compareAndSet(expected2, this)) {
                    true
                } else {
                    array[index1].value = expected1
                    false
                }
            }
            else {
                val value = array[index1].value
                if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) value.receiveHelp(index1)

                return false
            }
        }

        private fun updateStatus(newStatus: Status) {
            status.compareAndSet(UNDECIDED, newStatus)
        }

        private fun setValues() {
            if (array[index1].compareAndSet(this, update1)) {
                array[index2].compareAndSet(this, update2)
            }
        }

        fun apply() {
            if (install()) {
                updateStatus(SUCCESS)
                setValues()
            }
            else {
                updateStatus(FAILED)
            }
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}