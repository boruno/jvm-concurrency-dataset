@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import day3.AtomicArrayWithCAS2AndImplementedDCSS.Status.*
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
        val state = array[index]
        return if (state is AtomicArrayWithCAS2AndImplementedDCSS<*>.CAS2Descriptor) {
            if (state.status.get() == SUCCESS) {
                state.getUpdate(index)
            } else {
                state.getExpected(index)
            } as E
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
            logically(install())
            physically()
        }

        private fun physically() {
            if (status.get() == SUCCESS) {
                array.compareAndSet(index1, this, update1)
                array.compareAndSet(index2, this, update2)
            } else {
                array.compareAndSet(index1, this, expected1)
                array.compareAndSet(index2, this, expected2)
            }
        }

        private fun logically(success: Boolean) {
            status.compareAndSet(UNDECIDED, if (success) SUCCESS else FAILED)
        }

        private fun install(): Boolean {
            return if (install(index1, expected1, this)) {
                install(index2, expected2, this)
            } else {
                false
            }
        }

        private fun install(index: Int, expected: E, newValue: Any): Boolean {
            while (true) {
                when (val state = array.get(index)) {
                    this -> return true
                    is AtomicArrayWithCAS2AndImplementedDCSS<*>.CAS2Descriptor -> state.apply()
                    expected -> {
                        if (!dcss(index, expected, newValue, status, UNDECIDED) && status.get() != UNDECIDED) {
                            return false
                        }
                    }
                    else -> return false
                }
            }
        }

        fun getUpdate(index: Int): E {
            return when (index) {
                index1 -> update1
                index2 -> update2
                else -> error("Unknown index $index")
            }
        }

        fun getExpected(index: Int): E {
            return when (index) {
                index1 -> expected1
                index2 -> expected2
                else -> error("Unknown index $index")
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