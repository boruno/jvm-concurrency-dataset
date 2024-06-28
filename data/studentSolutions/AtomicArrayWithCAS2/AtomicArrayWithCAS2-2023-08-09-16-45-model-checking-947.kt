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

    fun get(index: Int): E {
        val state = array[index]
        return if (state is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
            if (state.status.get() == SUCCESS) {
                state.getUpdateValue(index) as E
            } else {
                state.getExpectedValue(index) as E
            }
        } else {
            state as E
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
            updateCells()
        }

        fun install(): Boolean {
            if (!install(index1, expected1)) return false
            return install(index2, expected2)
        }

        fun install(index: Int, expected: E): Boolean {
            if (status.get() != UNDECIDED) return false
            while (true) {
                when (val cellState = array[index]) {
                    this -> {
                        return true
                    }

                    is AtomicArrayWithCAS2<*>.CAS2Descriptor -> {
                        cellState.apply()
                    }

                    expected -> {
                        if (dcss(index, expected, this, status, UNDECIDED)) {
                            return true
                        } else {
                            if (status.get() != UNDECIDED) {
                                return true
                            }
                        }
                    }

                    else -> {
                        return false
                    }
                }
            }
        }

        fun updateStatus(success: Boolean) {
            status.compareAndSet(UNDECIDED, if (success) SUCCESS else FAILED)
        }

        fun updateCells() {
            if (status.get() == SUCCESS) {
                array.compareAndSet(index1, this, update1)
                array.compareAndSet(index2, this, update2)
            } else {
                array.compareAndSet(index1, this, expected1)
                array.compareAndSet(index2, this, expected2)
            }
        }

        fun getExpectedValue(index: Int): E {
            return if (index == index1) expected1 else expected2
        }

        fun getUpdateValue(index: Int): E {
            return if (index == index1) update1 else update2
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }

    fun dcss(
        index: Int,
        expectedCellState: Any?,
        updateCellState: Any?,
        statusReference: AtomicReference<*>,
        expectedStatus: Any?,
    ): Boolean {
       val descriptor = DcssDescriptor(index, expectedCellState, updateCellState, statusReference, expectedStatus)
       descriptor.apply()
       return descriptor.status.get() == SUCCESS
    }

    inner class DcssDescriptor(
        private val index: Int,
        private val expected: Any?,
        private val update: Any?,
        private val statusReference: AtomicReference<*>,
        private val expectedStatus: Any?
    ) {
        val status = AtomicReference(UNDECIDED)

        fun apply() {
            val success = install()
            updateStatus(success)
            updateCell()
        }

        private fun install(): Boolean {
            if (status.get() != UNDECIDED) return false
            while (true) {
                when (val cellState = array[index]) {
                    this -> {
                        return true
                    }

                    is AtomicArrayWithCAS2<*>.DcssDescriptor -> {
                        cellState.apply()
                    }

                    expected -> {
                        if (array.compareAndSet(index, expected, this)) {
                            return true
                        }
                    }

                    else -> {
                        return false
                    }
                }
            }
        }

        private fun updateStatus(success: Boolean) {
            if (success && array.get(index) == this && statusReference.get() == expectedStatus) {
                status.compareAndSet(UNDECIDED, SUCCESS)
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }
        }

        private fun updateCell() {
            if (status.get() == SUCCESS) {
                array.compareAndSet(index, this, update)
            } else {
                array.compareAndSet(index, this, expected)
            }
        }
    }
}