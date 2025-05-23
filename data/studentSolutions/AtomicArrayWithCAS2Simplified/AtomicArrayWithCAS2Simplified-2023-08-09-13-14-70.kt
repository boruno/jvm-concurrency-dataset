@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import AtomicArrayWithCAS2Simplified.Status.*
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray


// This implementation never stores `null` values.
class AtomicArrayWithCAS2Simplified<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    fun get(index: Int): E {
        // TODO: the cell can store CAS2Descriptor
        val value = array[index]
        if (value is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            if (value.status.get() == AtomicArrayWithCAS2SingleWriter.Status.UNDECIDED || value.status.get() == AtomicArrayWithCAS2SingleWriter.Status.FAILED) {
                if (index == value.index1) {
                    return value.expected1 as E
                } else {
                    return value.expected2 as E
                }
            } else {
                if (index == value.index1) {
                    return value.update1 as E
                } else {
                    return value.update2 as E
                }
            }
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
        return descriptor.status.get() === SUCCESS
    }

    inner class CAS2Descriptor(
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E,
        private val update2: E
    ) {
        val status = AtomicReference(UNDECIDED)

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            if (this.index1 > this.index2) {
                val orderDescriptor = CAS2Descriptor(index2, expected2, update2, index1, expected1, update1)
                orderDescriptor.apply()
                if (orderDescriptor.status.get() == SUCCESS) {
                    this.status.compareAndSet(UNDECIDED, SUCCESS)
                } else {
                    this.status.compareAndSet(UNDECIDED, FAILED)
                }
                return
            }
            if (array.compareAndSet(this.index1, this.expected1, this)) {
                if (array.compareAndSet(this.index2, this.expected2, this)) {
                    this.status.compareAndSet(UNDECIDED, SUCCESS)
                    while (!array.compareAndSet(index1, this, update1) || !array.compareAndSet(index2, this, update2)) {
                    }
                } else {
                    this.status.compareAndSet(UNDECIDED, FAILED)
                    array.compareAndSet(index1, this, expected1)
                }
            } else {
                if (array.get(index1) is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    val descriptorToHelp: CAS2Descriptor =
                        array.get(index1) as AtomicArrayWithCAS2Simplified<E>.CAS2Descriptor
                    if (array.compareAndSet(descriptorToHelp.index2, descriptorToHelp.expected2, descriptorToHelp)) {
                        descriptorToHelp.status.compareAndSet(UNDECIDED, SUCCESS)
                        while (!array.compareAndSet(index1, descriptorToHelp, descriptorToHelp.update1) || !array.compareAndSet(index2, descriptorToHelp, descriptorToHelp.update2)
                        ) {
                        }
                        this.apply()
                    } else {
                        descriptorToHelp.status.compareAndSet(UNDECIDED, FAILED)
                    }
                } else {
                    this.status.compareAndSet(UNDECIDED, FAILED)
                }
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}