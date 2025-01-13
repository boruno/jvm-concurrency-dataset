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
        val value = array[index].value
        if (value is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            return value.get(index) as E
        }
        return value as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: use CAS2Descriptor to fix it.
        val value = array[index1].value
        if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            finishUp(value)
        }

        val descriptor = CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        return descriptor.apply()
    }

    fun finishUp(descriptor: AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
        when (descriptor.status.value) {
            UNDECIDED -> {
                // check for cycles?
                val value2 = array[descriptor.index2].value
                if (value2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    finishUp(value2)
                }
                if (array[descriptor.index2].compareAndSet(descriptor.expected2, descriptor)) {
                    descriptor.status.compareAndSet(UNDECIDED, SUCCESS)
                    array[descriptor.index1].compareAndSet(descriptor, descriptor.update1)
                    array[descriptor.index2].compareAndSet(descriptor, descriptor.update2)
                } else {
                    descriptor.status.compareAndSet(UNDECIDED, FAILED)
                    array[descriptor.index1].compareAndSet(descriptor, descriptor.expected1)
                }
            }
            SUCCESS -> {
                array[descriptor.index1].compareAndSet(descriptor, descriptor.update1)
                array[descriptor.index2].compareAndSet(descriptor, descriptor.update2)
            }
            FAILED -> {
                array[descriptor.index1].compareAndSet(descriptor, descriptor.expected1)
            }
        }
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
            if (array[index1].compareAndSet(expected1, this)) {
                if (array[index2].compareAndSet(expected2, this)) {
                    status.compareAndSet(UNDECIDED, SUCCESS)
                    array[index1].compareAndSet(this, update1)
                    array[index2].compareAndSet(this, update2)
                    return true
                }
                status.compareAndSet(UNDECIDED, FAILED)
                array[index1].compareAndSet(this, expected1)
            }
            return false
        }

        fun get(index: Int): E {

            return if (status.value == SUCCESS) {
                when (index) {
                    index1 -> update1
                    index2 -> update2
                    else -> error("No such index in this descriptor")
                }
            } else {
                when (index) {
                    index1 -> expected1
                    index2 -> expected1
                    else -> error("No such index in this descriptor")
                }
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}