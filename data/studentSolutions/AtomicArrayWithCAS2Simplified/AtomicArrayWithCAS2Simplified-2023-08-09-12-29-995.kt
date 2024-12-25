@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import day3.AtomicArrayWithCAS2Simplified.Status.FAILED
import day3.AtomicArrayWithCAS2Simplified.Status.SUCCESS
import day3.AtomicArrayWithCAS2Simplified.Status.UNDECIDED
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

    fun get(index: Int): E = when (val element = array[index]) {
        is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
            if (element.status.get() === SUCCESS) {
                if (element.index1 == index) {
                    element.update1 as E
                } else {
                    element.update2 as E
                }
            } else {
                if (element.index1 == index) {
                    element.expected1 as E
                } else {
                    element.expected2 as E
                }
            }
        }

        else -> element as E
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
        val index1: Int, val expected1: E, val update1: E,
        val index2: Int, val expected2: E, val update2: E
    ) {
        val status = AtomicReference(UNDECIDED)

        fun apply() {
            val result = installDescriptor()
            updateStatus(result)
            updateCells(result)
        }

        private fun installDescriptor(): Boolean {
            if (array.compareAndSet(index1, expected1, this)) {
                return installTwoOrHelp()
            } else {
                val descriptor = array[index1] as? AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor ?: return false
                val result = if (index1 == descriptor.index1) {
                    array.compareAndSet(descriptor.index2, descriptor.expected2, descriptor)
                            || array[index2] == descriptor
                } else {
                    array.compareAndSet(descriptor.index1, descriptor.expected1, descriptor)
                            || array[index1] == descriptor
                }
                descriptor.updateStatus(result)
                descriptor.updateCells(result)
                return installDescriptor()
            }
        }

        private fun installTwoOrHelp(): Boolean {
            if (array.compareAndSet(index2, expected2, this)) {
                return true
            }
            val descriptor = array[index2] as? AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor ?: return false
            val result = if (index2 == descriptor.index2) {
                array.compareAndSet(descriptor.index1, descriptor.expected1, descriptor)
                        || array[index1] == descriptor
            } else {
                array.compareAndSet(descriptor.index2, descriptor.expected2, descriptor)
                        || array[index2] == descriptor
            }
            descriptor.updateStatus(result)
            descriptor.updateCells(result)
            return installTwoOrHelp()
        }

        private fun updateStatus(result: Boolean) {
            status.compareAndSet(UNDECIDED, if (result) SUCCESS else FAILED)
        }

        private fun updateCells(result: Boolean) {
            array.compareAndSet(index1, this, if (result) update1 else expected1)
            array.compareAndSet(index2, this, if (result) update2 else expected2)
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}