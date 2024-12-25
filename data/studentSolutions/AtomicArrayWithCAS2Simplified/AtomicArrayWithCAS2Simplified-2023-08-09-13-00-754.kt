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

        private fun installDescriptor() = if (index1 < index2) {
            installDescriptor(index1, expected1, index2, expected2)
        } else {
            installDescriptor(index2, expected2, index1, expected1)
        }

        private fun installDescriptor(i1: Int, e1: E, i2: Int, e2: E): Boolean {
            val curStatus = status.get()
            if (curStatus != UNDECIDED) {
                return curStatus == SUCCESS
            }
            if (array.compareAndSet(i1, e1, this)) {
                return installTwoOrHelp(i2, e2)
            } else {
                val descriptor = array[i1] as? AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor
                    ?: return if (array[i1] == e1) {
                        installDescriptor(i1, e1, i2, e2)
                    } else {
                        false
                    }
                val descStatus = descriptor.status.get()
                val result = if (descStatus === UNDECIDED) {
                    if (i1 == descriptor.index1) {
                        array.compareAndSet(descriptor.index2, descriptor.expected2, descriptor)
                                || array[descriptor.index2] == descriptor
                    } else {
                        array.compareAndSet(descriptor.index1, descriptor.expected1, descriptor)
                                || array[descriptor.index1] == descriptor
                    }
                } else {
                    descStatus === SUCCESS
                }
                descriptor.updateStatus(result)
                descriptor.updateCells(result)
                return installDescriptor(i1, e1, i2, e2)
            }
        }

        private fun installTwoOrHelp(i2: Int, e2: E): Boolean {
            val curStatus = status.get()
            if (curStatus != UNDECIDED) {
                return curStatus == SUCCESS
            }
            if (array.compareAndSet(i2, e2, this)) {
                return true
            }
            val descriptor = array[i2] as? AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor
                ?: return if (array[i2] == e2) {
                    installTwoOrHelp(i2, e2)
                } else {
                    false
                }
            val descStatus = descriptor.status.get()
            val result = if (descStatus === UNDECIDED) {
                if (i2 == descriptor.index2) {
                    array.compareAndSet(descriptor.index1, descriptor.expected1, descriptor)
                            || array[descriptor.index1] == descriptor
                } else {
                    array.compareAndSet(descriptor.index2, descriptor.expected2, descriptor)
                            || array[descriptor.index2] == descriptor
                }
            } else {
                descStatus === SUCCESS
            }
            descriptor.updateStatus(result)
            descriptor.updateCells(result)
            return installTwoOrHelp(i2, e2)
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