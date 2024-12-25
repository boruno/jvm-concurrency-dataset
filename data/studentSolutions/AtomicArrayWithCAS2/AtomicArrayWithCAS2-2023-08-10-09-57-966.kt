@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import day3.AtomicArrayWithCAS2.Status.*
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray

// This implementation never stores `null` values.
class AtomicArrayWithCAS2<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    fun get(index: Int): E? {
        // TODO: the cell can store a descriptor
        val value = array[index]
        return get(index, value)
    }

    private fun get(index: Int, value: Any?): E? {
        return when (value) {
            is AtomicArrayWithCAS2<*>.CAS2Descriptor ->
                if (value.status.get() == SUCCESS) {
                    value.updateForIndex(index)
                } else {
                    value.expectedForIndex(index)
                }

            is AtomicArrayWithCAS2<*>.DCSSDescriptor -> {
                if (value.status.get() == SUCCESS) {
                    get(value.index, value.updateCellState)
                } else {
                    get(value.index, value.expectedCellState)
                }
            }

            else -> value
        } as E?

    }

    fun cas2(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?, update2: E?
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
        val expected1: E?,
        val update1: E?,
        val index2: Int,
        val expected2: E?,
        val update2: E?
    ) {
        val status = AtomicReference(UNDECIDED)

        fun updateForIndex(index: Int): E? = if (index == index1) update1 else update2
        fun expectedForIndex(index: Int): E? = if (index == index1) expected1 else expected2


        fun apply() {
            // TODO: Copy the implementation from `AtomicArrayWithCAS2Simplified`
            // TODO: and use `dcss(..)` to install the descriptor.
            val success = install()
            updateStatus(success)
            updatePhysically()
        }

        private fun install(): Boolean {
            if (!install(index1, expected1)) {
                return false
            }
            return install(index2, expected2)
        }

        private fun install(index: Int, expected: E?): Boolean {
            while (true) {
                if (status.get() != UNDECIDED) return false
                when (val cellState = array[index]) {
                    this -> {
                        return true
                    }

                    is AtomicArrayWithCAS2<*>.CAS2Descriptor -> {
                        cellState.apply()
                    }

                    is AtomicArrayWithCAS2<*>.DCSSDescriptor -> {
                        cellState.apply()
                    }

                    expected -> {
                        if (dcss(index, expected, this, status, UNDECIDED)) {
                            return true
                        } else {
                            continue
                        }
                    }

                    else -> { // unexpected value
                        return false
                    }
                }
            }
        }

        private fun updateStatus(success: Boolean) {
            status.compareAndSet(UNDECIDED, if (success) SUCCESS else FAILED)
        }

        private fun updatePhysically() {
            val success = status.get() == SUCCESS
            if (success) {
                array.compareAndSet(index1, this, update1)
                array.compareAndSet(index2, this, update2)
            } else {
                array.compareAndSet(index1, this, expected1)
                array.compareAndSet(index2, this, expected2)
            }
        }

    }

    // TODO: Please use this DCSS implementation to ensure that
    // TODO: the status is `UNDECIDED` when installing the descriptor.
    fun dcss(
        index: Int,
        expectedCellState: E?,
        updateCellState: Any?,
        statusReference: AtomicReference<Status>,
        expectedStatus: Status
    ): Boolean {

        val descriptor = DCSSDescriptor(index, expectedCellState, updateCellState, statusReference, expectedStatus)
        descriptor.apply()
        return descriptor.status.get() === SUCCESS
    }

    inner class DCSSDescriptor(
        val index: Int,
        val expectedCellState: E?,
        val updateCellState: Any?,
        private val statusReference: AtomicReference<Status>,
        private val expectedStatus: Status
    ) {

        val status = AtomicReference(UNDECIDED)

        fun apply() {
            val success = install() && status.get() == UNDECIDED && statusReference.get() == expectedStatus
            updateStatus(success)
            updatePhysically()
        }

        private fun install(): Boolean {
            while (true) {
                if (status.get() != UNDECIDED) return false
                when (val cellState = array[index]) {
                    this -> {
                        return true
                    }

                    is AtomicArrayWithCAS2<*>.CAS2Descriptor -> {
                        cellState.apply()
                    }

                    is AtomicArrayWithCAS2<*>.DCSSDescriptor -> {
                        cellState.apply()
                    }

                    expectedCellState -> {
                        return array.compareAndSet(index, expectedCellState, this)
                    }

                    else -> { // unexpected value
                        return false
                    }
                }
            }
//            return array.compareAndSet(index, expectedCellState, this) || array[index] == this
        }

        private fun updateStatus(success: Boolean) {
            status.compareAndSet(UNDECIDED, if (success) SUCCESS else FAILED)
        }

        private fun updatePhysically() {
            array.compareAndSet(
                index, this,
                if (status.get() === SUCCESS) updateCellState else expectedCellState
            )
        }

    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }

}