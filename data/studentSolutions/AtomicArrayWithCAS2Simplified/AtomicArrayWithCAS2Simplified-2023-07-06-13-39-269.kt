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
                    } else {
                        cellValue.update2
                    }
                }

                UNDECIDED, FAILED -> if (index == cellValue.index1) {
                    cellValue.expected1
                } else {
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
        val descriptor = if (index1 < index2) {
            CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        }
        else {
            CAS2Descriptor(index2, expected1, update1, index1, expected2, update2)
        }
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

        fun apply(): Boolean {
            // TODO: install the descriptor, update the status, update the cells.
            // set descriptors
            if (tryInstallDescriptors()) {
                status.compareAndSet(UNDECIDED, SUCCESS)
            }
            else {
                status.compareAndSet(UNDECIDED, FAILED)
            }

            // try setting values
            if (status.value == SUCCESS) {
                setValues(index1, index2, update1, update2)
                return true
            }

            setValues(index1, index2, expected1, expected2)
            return false
        }

        private fun CAS2Descriptor.tryInstallDescriptors() =
            setDescriptor(index1, expected1) && setDescriptor(index2, expected2)


        private fun setValues(i1: Int, i2: Int, updated1: Any, updated2: Any) {
            array[i1].compareAndSet(this, updated1)
            array[i2].compareAndSet(this, updated2)
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
                    setValues(descriptor.index1, descriptor.index2, descriptor.update1, descriptor.update2)
                    // retry
                    return setDescriptor(index, expected)
                }

                UNDECIDED -> {
                    descriptor.apply()
                    return setDescriptor(index, expected)
                }

                FAILED -> {
                    setValues(descriptor.index1, descriptor.index2, descriptor.expected1, descriptor.expected2)
                    // retry
                    return setDescriptor(index, expected)
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