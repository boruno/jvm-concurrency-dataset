@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

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

    fun get(index: Int): E {
        // TODO: Copy the implementation from `AtomicArrayWithCAS2Simplified`
        val state = array[index]
        return when {
            state is AtomicArrayWithCAS2<*>.CAS2Descriptor -> state.getCellVal(index) as E
            state is AtomicArrayWithCAS2<*>.DCSS2Descriptor -> state.getCellVal(index) as E
            else -> state as E
        }
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

    fun dcss(
        index: Int,
        expectedCellState: E,
        updateCellState: CAS2Descriptor,
        statusReference: AtomicReference<*>,
        expectedStatus: Any?
    ): Boolean {
        val descriptor =
            DCSS2Descriptor(
                index = index,
                expected = expectedCellState,
                update = updateCellState,
                expectedStatus = expectedStatus,
                statusReference = statusReference
            )
        descriptor.apply()
        return descriptor.status.get() === SUCCESS
    }

    inner class DCSS2Descriptor(
        val index: Int,
        val expected: E,
        val update: CAS2Descriptor,
        val expectedStatus: Any?,
        val statusReference: AtomicReference<*>,
    ) {
        val status = AtomicReference(UNDECIDED)

        fun getCellVal(index: Int): E {
            return if (status.get() == SUCCESS) {
                update.getCellVal(index)
            } else {
                expected
            }
        }

        fun apply() {
            val success = install()
            updateStatus(success)
            updatePhysically()
        }

        private fun install(): Boolean {
            while (true) {
                if (status.get() != UNDECIDED) return false
                val currentCell = array.get(this.index)
                return when (currentCell) {
                    this -> true
                    expected -> array.compareAndSet(index, expected, this)
                    else -> false
                }
            }
        }

        private fun updateStatus(success: Boolean) {
            status.compareAndSet(UNDECIDED, if (success && statusReference.get() == expectedStatus) SUCCESS else FAILED)
        }

        private fun updatePhysically() {
            if (status.get() == SUCCESS) {
                array.compareAndSet(index, this, update)
            } else {
                array.compareAndSet(index, this, expected)
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
        val status = AtomicReference(UNDECIDED)

        fun getCellVal(index: Int): E {
            return if (status.get() == SUCCESS) {
                if (index1 == index) update1 else update2
            } else {
                if (index1 == index) expected1 else expected2
            }
        }

        fun apply() {
            // TODO: Copy the implementation from `AtomicArrayWithCAS2Simplified`
            // TODO: and use `dcss(..)` to install the descriptor.


            val success = install()
            updateStatus(success)
            updatePhysically()
        }


        private fun install(): Boolean {
            return install(index1, expected1) && install(index2, expected2)
        }

        private fun install(index: Int, expected: E): Boolean {
            while (true) {
                if (status.get() != UNDECIDED) return false
                val cellState = array[index]
                when (cellState) {
                    this -> {
                        return true
                    }

                    is AtomicArrayWithCAS2<*>.CAS2Descriptor -> {
                        cellState.apply()
                    }

                    is AtomicArrayWithCAS2<*>.DCSS2Descriptor -> {
                        cellState.apply()
                    }

                    expected -> {
                        if (dcss(index, expected, this, status, UNDECIDED)) {
                            return true
                        } else {
                            continue
                        }
                    }

                    else -> { //unexpected value
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

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }

}