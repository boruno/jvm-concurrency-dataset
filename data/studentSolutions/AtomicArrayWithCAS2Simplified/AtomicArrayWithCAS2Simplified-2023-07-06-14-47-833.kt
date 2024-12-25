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
        val cellValue = array[index].value
        if (cellValue is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            val status = cellValue.status.value
            return when (status) {
                SUCCESS -> {
                    if (index == cellValue.index1) {
                        cellValue.update1
                    } else {
                        cellValue.update2
                    }
                }

                UNDECIDED, FAILED -> if (index == cellValue.index1) {
                    cellValue.expected1
                } else {
                    cellValue.expected2
                }
            } as E
        }
        return cellValue as E

    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: use CAS2Descriptor to fix it.
        val descriptor = if (index1 < index2) {
            CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        } else {
            CAS2Descriptor(index2, expected2, update2, index1, expected1, update1)
        }
        descriptor.apply()
        return descriptor.status.value === SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            // TODO: install the descriptor, update the status, update the cells.
            if (status.value !== SUCCESS) return
            // set descriptors
            if (tryInstallDescriptors()) {
                status.compareAndSet(UNDECIDED, SUCCESS)
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }

            // try setting values
            updateValues()
        }

        private fun updateValues(): Boolean {
            return if (status.value === SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            } else {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
            }
        }

        private fun tryInstallDescriptors(): Boolean {
            if (!tryDescriptor(index1, expected1)) return false
            if (!tryDescriptor(index2, expected2)) return false

            return true
        }


        private fun tryDescriptor(index: Int, expected: E): Boolean {
            while (true) {
                val current = array[index].value
                when (current) {
                    expected -> {
                        val success = array[index].compareAndSet(expected, this)
                        if (success) {
                            return true
                        }
                    }

                    this -> {
                        return true
                    }

                    is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
//                        if (current.status.value !== SUCCESS) {
                            current.apply()
//                        }
                    }

                    else -> {
                        return false
                    }
                }

            }
        }

        private fun setDescriptor(index: Int, expected: E): Boolean {

            if (array[index].compareAndSet(expected, this)) {
                return true
            }

            // descriptor set failed

            // is here another descriptor?
            val cellContent = array[index].value
            if (cellContent !is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) return false

            // is descriptor mine?
            val descriptor: AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor = cellContent
            if (descriptor == this) return true

            // another descriptor
            // check status
            val status = descriptor.status.value
            when (status) {
                SUCCESS -> {
                    array[descriptor.index1].compareAndSet(this, descriptor.update1)
                    array[descriptor.index2].compareAndSet(this, descriptor.update2)
                    // retry
                    return setDescriptor(index, expected)
                }

                UNDECIDED -> {
                    descriptor.apply()
                    return setDescriptor(index, expected)
                }

                FAILED -> {
                    array[descriptor.index1].compareAndSet(this, descriptor.expected1)
                    array[descriptor.index2].compareAndSet(this, descriptor.expected2)
                    // retry
                    return setDescriptor(index, expected)
                }
            }
        }

    }


    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}