@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

package day3

import day3.AtomicArrayWithCAS2AndImplementedDCSS.Status.*
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray

// This implementation never stores `null` values.
class AtomicArrayWithCAS2AndImplementedDCSS<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    fun get(index: Int): E {
        val result = array[index]
        if (result is AtomicArrayWithCAS2AndImplementedDCSS<*>.CAS2Descriptor) {
            return if (result.status.get() == SUCCESS)
                result.getUpdatedByIndex(index) as E
            else
                result.getExpectedByIndex(index) as E
        }
        return result as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = if (index1 < index2) {
            CAS2Descriptor(
                index1 = index1, expected1 = expected1, update1 = update1,
                index2 = index2, expected2 = expected2, update2 = update2
            )
        } else {
            CAS2Descriptor(
                index1 = index2, expected1 = expected2, update1 = update2,
                index2 = index1, expected2 = expected1, update2 = update1
            )
        }
        descriptor.apply()
        return descriptor.status.get() === SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = AtomicReference(UNDECIDED)

        fun getExpectedByIndex(index: Int) = if (index == index1) expected1 else expected2
        fun getUpdatedByIndex(index: Int) = if (index == index1) update1 else update2

        fun apply() {
            val descriptorsInstalled = installDescriptors()
            updateStatus(descriptorsInstalled)
            updateCells()
        }


        private fun installDescriptors(): Boolean {
            return installDescriptor(index1, expected1) && installDescriptor(index2, expected2)
        }

        private fun installDescriptor(index: Int, expected: E): Boolean {
            if (status.get() != UNDECIDED) return false

            while (true) {
                val current = array.get(index)

                if (current === this) return true

                if (current is AtomicArrayWithCAS2AndImplementedDCSS<*>.CAS2Descriptor) {
                    current.apply()
                    continue
                }

                if (current == expected) {
                    if (dcss(index, expected, this, status, UNDECIDED)) return true
                    continue
                }

                return false
            }
        }

        private fun updateStatus(descriptorsInstalled: Boolean) {
            status.compareAndSet(UNDECIDED, if (descriptorsInstalled) SUCCESS else FAILED)
        }

        private fun updateCells() {
            if (status.get() == SUCCESS) {
                array.compareAndSet(index1, this, update1)
                array.compareAndSet(index2, this, update2)
            } else {
                array.compareAndSet(index1, this, expected1)
                array.compareAndSet(index2, this, expected2)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }

    // TODO: Please use this DCSS implementation to ensure that
    // TODO: the status is `UNDECIDED` when installing the descriptor.
    fun dcss(
        index: Int,
        expectedCellState: Any?,
        updateCellState: Any?,
        statusReference: AtomicReference<*>,
        expectedStatus: Any?
    ): Boolean =
        if (array[index] == expectedCellState && statusReference.get() == expectedStatus) {
            array[index] = updateCellState
            true
        } else {
            false
        }
}