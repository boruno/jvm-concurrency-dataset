//package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
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

    fun get(index: Int): E {
        // TODO: the cell can store CAS2Descriptor
        val value = array[index].value
        if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            if (value.status.value === SUCCESS) {
                return if (value.index1 == index)
                    value.update1 as E
                else
                    value.update2 as E
            }
            return if (value.index1 == index)
                value.expected1 as E
            else
                value.expected2 as E
        } else {
            return value as E
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = if (index1 < index2) CAS2Descriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2, update2 = update2
        )
        else CAS2Descriptor(
            index1 = index2, expected1 = expected2, update1 = update2,
            index2 = index1, expected2 = expected1, update2 = update1
        )
        descriptor.apply()
        return descriptor.status.value === SUCCESS
    }

    private inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        init {
          require(index1 <= index2)
        }

        val status = atomic(UNDECIDED)

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            if (install()) {
                setStatus(SUCCESS)
            } else {
                setStatus(FAILED)
            }
            updateCells()
        }

        private fun install(): Boolean {
            return install(index1, expected1) && install(index2, expected2)
        }

        private fun install(index: Int, expected: E): Boolean {
            while (true) {
                if (array[index].compareAndSet(expected, this))
                    return true

                val value = array[index].value
                if (value === this)
                    return true

                if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    when (value.status.value) {
                        UNDECIDED -> {
                            when (index) {
                                value.index1 -> {
                                    val installed = (value as AtomicArrayWithCAS2Simplified<Any>.CAS2Descriptor)
                                        .install(value.index2, value.expected2)
                                    val newStatus = if (installed) SUCCESS else FAILED
                                    value.status.compareAndSet(UNDECIDED, newStatus)
                                }
                                value.index2 -> value.setStatus(SUCCESS)
                                else -> throw IllegalStateException()
                            }
                        }
                        SUCCESS, FAILED -> value.updateCells()
                    }
                } else {
                    return false
                }
            }
        }

        private fun isInstalled(descriptor: CAS2Descriptor) {

        }

        private fun setStatus(value: Status) {
            status.value = value
        }

        private fun updateCells() {
            when (status.value) {
                UNDECIDED -> throw IllegalStateException()
                SUCCESS -> {
                    array[index1].compareAndSet(this, update1)
                    array[index2].compareAndSet(this, update2)
                }

                FAILED -> {
                    array[index1].compareAndSet(this, expected1)
                    array[index2].compareAndSet(this, expected2)
                }
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}