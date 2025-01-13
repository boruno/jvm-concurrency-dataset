//package day3

import AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls


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
        return if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            value.getValue(index)
        } else {
            value
        } as E
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

    private inner class CAS2Descriptor(
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E,
        private val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun getValue(index: Int): E {
            require(index == index1 || index == index2)
            return if (status.value === SUCCESS) {
                if (index1 == index) {
                    update1
                } else {
                    update2
                }
            } else {
                if (index1 == index) {
                    expected1
                } else {
                    expected2
                }
            }
        }

        fun apply() {
            install()
            updateStatus()
            updateCells()
        }

        private fun install() {
            if (install(index1, expected1)) {
                install(index2, expected2)
            }
        }

        private fun install(index: Int, expected: E): Boolean {
            while (true) {
                if (array[index].compareAndSet(expected, this)) {
                    return true
                }
                val value = array[index].value
                if (value === this) {
                    // this descriptor is already installed
                    return true
                }
                if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    // help other descriptor to complete
                    value.apply()
                    continue
                }
                if (value !== expected) {
                    return false
                }
            }
        }

        private fun updateStatus() {
            val value = array[index2].value
            val newStatus = if (value === this) SUCCESS else FAILED
            status.compareAndSet(UNDECIDED, newStatus)
        }

        private fun updateCells() {
            val pair = when (status.value) {
                UNDECIDED -> throw IllegalStateException()
                SUCCESS -> update1 to update2
                FAILED -> expected1 to expected2
            }
            array[index1].compareAndSet(this, pair.first)
            array[index2].compareAndSet(this, pair.second)
        }
    }

    private enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}