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
        // TODO: the cell can store CAS2Descriptor
        val cellValue = array[index].value
        if (cellValue is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            val status = cellValue.status.value
            return when (status) {
                SUCCESS -> {
                    if (index == cellValue.index1) {
                        cellValue.update1
                    }
                    else {
                        cellValue.update2
                    }
                }

                UNDECIDED, FAILED -> if (index == cellValue.index1) {
                    cellValue.expected1
                }
                else {
                    cellValue.expected2
                }
//                FAILED -> {
//
//                }
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
        return CAS2Descriptor(index1, expected1, update1, index2, expected2, update2).apply()
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

        fun apply(): Boolean {
            // TODO: install the descriptor, update the status, update the cells.
            return if (setDescriptor(index1, expected1) && setDescriptor(index2, expected2)) {
                status.compareAndSet(UNDECIDED, SUCCESS)
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
                true
            } else {
                status.compareAndSet(
                    UNDECIDED,
                    FAILED
                )
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
                false
            }
        }


        private fun setDescriptor(index: Int, expected: E): Boolean{
            if (array[index].compareAndSet(expected, this)) return true

            // descriptor set failed

            // is here another descriptor?
            val descriptor = array[index].value
            if (descriptor !is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) return false

            // another descriptor
            // check status
            val status = descriptor.status.value
            return when(status) {
                SUCCESS -> {
                    array[index].compareAndSet(descriptor, descriptor.update1)
                    array[index].compareAndSet(descriptor, descriptor.update2)
                    // retry
                    setDescriptor(index, expected)
                }
                UNDECIDED -> {
                    if (setDescriptor(descriptor.index1, descriptor.expected1 as E) &&
                    setDescriptor(descriptor.index2, descriptor.expected2 as E)) {
                        descriptor.status.compareAndSet(UNDECIDED, SUCCESS)
                    }
                    setDescriptor(index, expected)
                }
                FAILED -> {
                    array[index].compareAndSet(descriptor, descriptor.expected1)
                    array[index].compareAndSet(descriptor, descriptor.expected2)
                    // retry
                    setDescriptor(index, expected)
                }
            }
        }

        private fun findExpected(index: Int) {
            if (index == index1) expected1 else expected2
        }

        private fun findUpdate(index: Int) {
            if (index == index1) update1 else update2
        }

    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}