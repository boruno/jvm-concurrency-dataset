@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import AtomicArrayWithCAS2Simplified.Status.FAILED
import AtomicArrayWithCAS2Simplified.Status.SUCCESS
import AtomicArrayWithCAS2Simplified.Status.UNDECIDED
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
        val descriptor = if (index1 < index2) CAS2Descriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2, update2 = update2
        ) else CAS2Descriptor(
            index1 = index2, expected1 = expected2, update1 = update2,
            index2 = index1, expected2 = expected1, update2 = update1
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
            updateCells()
        }

        private fun installDescriptor(): Boolean {
            if (!install(index1, expected1)) {
                return false
            }
            return install(index2, expected2)
        }

        private fun install(index: Int, expected: E): Boolean {
            val curStatus = status.get()
            if (curStatus !== UNDECIDED) {
                return curStatus == SUCCESS
            }
            return when (val element = array[index]) {
                this -> true
                expected -> array.compareAndSet(index, expected, this)
                is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
                    element.apply()
                    install(index, expected)
                }

                else -> false
            }
        }

        private fun installTwoOrHelp(): Boolean {
            val curStatus = status.get()
            if (curStatus !== UNDECIDED) {
                return curStatus == SUCCESS
            }
            if (array.compareAndSet(index2, expected2, this)) {
                return true
            }
            return help(index2, expected2, ::installTwoOrHelp)
        }

        private fun help(index: Int, expected: E, retry: () -> Boolean): Boolean {
            val descriptor = array[index] as? AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor
                ?: return if (array[index] == expected) {
                    return retry()
                } else {
                    false
                }
            val descStatus = descriptor.status.get()
            val result = if (descStatus !== UNDECIDED) {
                descStatus === SUCCESS
            } else {
                if (index == descriptor.index1) {
                    array.compareAndSet(descriptor.index2, descriptor.expected2, descriptor)
                            || array[descriptor.index2] == descriptor
                } else {
                    array.compareAndSet(descriptor.index1, descriptor.expected1, descriptor)
                            || array[descriptor.index1] == descriptor
                }
            }
            descriptor.updateStatus(result)
            descriptor.updateCells()
            return retry()
        }

        private fun updateStatus(result: Boolean) {
            status.compareAndSet(UNDECIDED, if (result) SUCCESS else FAILED)
        }

        private fun updateCells() {
            val result = status.get() == SUCCESS
            array.compareAndSet(index1, this, if (result) update1 else expected1)
            array.compareAndSet(index2, this, if (result) update2 else expected2)
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}