@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

package day3

import day3.AtomicArrayWithCAS2.Status.*
import java.util.concurrent.atomic.*

// This implementation never stores `null` values.
class AtomicArrayWithCAS2<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    tailrec fun get(index: Int): E = when (val value = array[index]) {
        is AtomicArrayWithCAS2<*>.CAS2Descriptor -> when (value.status.get()!!) {
            SUCCESS -> if (index == value.index1) value.update1 else value.update2
            FAILED, UNDECIDED -> if (index == value.index1) value.expected1 else value.expected2
        } as E
        is AtomicArrayWithCAS2<*>.DCSSDescriptor -> {
            value.apply()
            get(index)
        }
        else -> value as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val exchange = index1 > index2
        val descriptor = if (exchange) CAS2Descriptor(
            index1 = index2, expected1 = expected2, update1 = update2,
            index2 = index1, expected2 = expected1, update2 = update1,
        ) else CAS2Descriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2, update2 = update2
        )
        descriptor.apply()
        return descriptor.status.get() === SUCCESS
    }

    private inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = AtomicReference(UNDECIDED)

        fun apply() {
            val success = updateLogically()
            updateStatus(success)
            updatePhysically()
        }

        private fun updatePhysically() {
            when (status.get()!!) {
                SUCCESS -> {
                    array.compareAndSet(index1, this, update1)
                    array.compareAndSet(index2, this, update2)
                }

                FAILED -> {
                    array.compareAndSet(index1, this, expected1)
                    array.compareAndSet(index2, this, expected2)
                }

                UNDECIDED -> error("Invalid state")
            }
        }

        private fun updateStatus(success: Boolean) {
            status.compareAndSet(UNDECIDED, if (success) SUCCESS else FAILED)
        }

        private fun updateLogically(index: Int, expected: E): Boolean {
            while (true) {
                when (status.get()!!) {
                    FAILED -> return false
                    SUCCESS -> return true
                    UNDECIDED -> when (val cellState = array[index]) {
                        this -> return true
                        is AtomicArrayWithCAS2<*>.CAS2Descriptor -> cellState.apply()
                        is AtomicArrayWithCAS2<*>.DCSSDescriptor -> cellState.apply()
                        expected -> {
                            if (dcss(index, expected, this, status, UNDECIDED)) {
                                return true
                            }
                        }

                        else -> return false
                    }
                }
            }
        }

        private fun updateLogically(): Boolean =
            updateLogically(index1, expected1) && updateLogically(index2, expected2)
    }

    private inner class DCSSDescriptor(
        val index: Int,
        val expectedCellState: Any?,
        val updateCellState: Any?,
        val statusReference: AtomicReference<*>,
        val expectedStatus: Any?,
    ) {
        fun apply(): Boolean {
            val newValue = if (statusReference.get() == expectedStatus) updateCellState else return false
            return array.compareAndSet(index, this, newValue)
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
    
    private fun dcss(
        index: Int,
        expectedCellState: Any?,
        updateCellState: Any?,
        statusReference: AtomicReference<*>,
        expectedStatus: Any?
    ): Boolean {
        val dcssDescriptor = DCSSDescriptor(index, expectedCellState, updateCellState, statusReference, expectedStatus)
        return array.compareAndSet(index,  expectedCellState, dcssDescriptor) && dcssDescriptor.apply()
    }
}
