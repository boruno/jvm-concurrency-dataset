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
        // TODO: Copy the implementation from `AtomicArrayWithCAS2Simplified`
        val state = array[index]
        if (state is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            state as AtomicArrayWithCAS2Simplified<E>.CAS2Descriptor
            return state.getValue(index)
        }
        return state as E
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

        fun getValue(index: Int): E {
            fun wrongIndex(): Nothing =
                error("Accessing wrong index $index. Descriptor works with indices $index1 and $index2")

            return when (status.get()!!) {
                UNDECIDED, FAILED -> if (index1 == index) expected1 else if (index2 == index) expected2 else wrongIndex()
                SUCCESS -> if (index1 == index) update1 else if (index2 == index) update2 else wrongIndex()
            }
        }
        fun apply() {
            // TODO: Copy the implementation from `AtomicArrayWithCAS2Simplified`
            // TODO: and use `dcss(..)` to install the descriptor.
            val success = installDescriptors()
            updateTheStatus(success)
            updatePhysically()
        }

        private fun installDescriptors(): Boolean {
            if (!installDescriptor(index1, expected1)) return false
            return installDescriptor(index2, expected2)
        }

        private fun installDescriptor(index: Int, expected: E): Boolean {
            if (status.get() != UNDECIDED) return false
            while (true) {
                val cellState = array[index]
                when (cellState) {
                    expected -> {
                        if (dcss(index, expected, this, status, UNDECIDED)) return true
//                        if (array.compareAndSet(index, expected, this)) return true
                    }
                    this -> {
                        return true
                    }
                    is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
                        cellState.apply()
                    }

                    else /* unexpected value */ -> {
                        return false
                    }
                }
            }
        }

        private fun updateTheStatus(success: Boolean) {
            status.compareAndSet(UNDECIDED, if (success) SUCCESS else FAILED)
        }

        private fun updatePhysically() {
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
            array.compareAndSet(index, expectedCellState, updateCellState)
//            array[index] = updateCellState
//            true
        } else {
            false
        }
}