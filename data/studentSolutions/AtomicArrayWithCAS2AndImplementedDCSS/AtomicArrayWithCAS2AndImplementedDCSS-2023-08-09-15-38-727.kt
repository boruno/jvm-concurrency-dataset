@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import AtomicArrayWithCAS2AndImplementedDCSS.Status.*
import java.util.concurrent.atomic.*

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
        val value = array[index]
        val descriptor = value as? AtomicArrayWithCAS2AndImplementedDCSS<*>.CAS2Descriptor
        if (descriptor != null) {
            if (descriptor.status.get() === Status.SUCCESS) {
                return if (index == descriptor.index1) (descriptor.update1 as E) else (descriptor.update2 as E)
            } else {
                return if (index == descriptor.index1) (descriptor.expected1 as E) else (descriptor.expected2 as E)
            }
        }

        return value as E
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

        fun apply() {
            val success = install()
            updateStatus(success)
            updatePhysically()
        }

        private fun install(): Boolean {
            if (!install(index1, expected1)) return false
            return install(index2, expected2)
        }

        private fun install(index: Int, expected: E): Boolean {
            if (status.get() != UNDECIDED) return false
            while (true) {
                when (val cellState = array.get(index)) {
                    this -> return true
                    is AtomicArrayWithCAS2AndImplementedDCSS<*>.CAS2Descriptor -> {
                        cellState.apply()
                    }
                    expected -> {
                        if (dcss(index, expected, this, this.status, UNDECIDED))
                            return true
                    }
                    else -> return false
                }
            }
        }

        fun updateStatus(success: Boolean) {
            status.compareAndSet(UNDECIDED, if (success) SUCCESS else FAILED)
        }

        fun updatePhysically() {
            val success = status.get() === SUCCESS
            if (success) {
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
            array.compareAndSet(index, expectedCellState, updateCellState)
//            array[index] = updateCellState
            true
        } else {
            false
        }
}