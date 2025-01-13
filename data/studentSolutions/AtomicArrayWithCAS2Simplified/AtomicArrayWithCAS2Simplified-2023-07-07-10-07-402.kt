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
        while (true) {
            val value = array[index].value
            if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                value.apply()
            } else {
                return value as E
            }
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: use CAS2Descriptor to fix it.
//        if (array[index1].value != expected1 || array[index2].value != expected2) return false
        val descriptor = if (index1 < index2) {
            CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        } else {
            CAS2Descriptor(index2, expected2, update2, index1, expected1, update1)
        }
        descriptor.tryInstallDescriptor(index1, expected1)
        descriptor.apply()
        return descriptor.status.value == SUCCESS
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
            // TODO: install the descriptor, update the status, update the cells.
            if (status.value == UNDECIDED) {
                val success = tryInstallDescriptor()
                val statusValue = if (success) SUCCESS else FAILED
                status.compareAndSet(UNDECIDED, statusValue)
            }

            updateValues()
        }

        private fun tryInstallDescriptor(): Boolean {
            return /*tryInstallDescriptor(index1, expected1) &&*/ tryInstallDescriptor(index2, expected2)
        }

        fun tryInstallDescriptor(index: Int, expected: E): Boolean {
            while (true) {
                val curState = array[index].value
                when {
                    curState === this -> {
                        return true // already installed
                    }
                    curState is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
                        curState.apply()
                    }
                    curState === expected -> {
                        if (array[index].compareAndSet(expected, this)) {
                            return true // successfully installed
                        } else {
                            continue // retry
                        }
                    }
                    else -> {
                        return false // value, not expected
                    }
                }
            }
        }

        private fun updateValues() {
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