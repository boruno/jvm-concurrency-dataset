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

    fun get(index: Int): Any? {
        // TODO: Copy the implementation from `AtomicArrayWithCAS2Simplified`
//        TODO("Implement me")
        val state = array[index]
        return if (state is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            val status = state.status.get()
            if (index == state.index1) {
                return if (status == AtomicArrayWithCAS2Simplified.Status.SUCCESS) state.update1 else state.expected1
            }
            if (status == AtomicArrayWithCAS2Simplified.Status.SUCCESS) state.update2 else state.expected2
        } else state
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
            // TODO: Copy the implementation from `AtomicArrayWithCAS2Simplified`
            // TODO: and use `dcss(..)` to install the descriptor.
            val success = tryToInstallDescriptor()
            updateCellsLogically(success)
            updateCellsPhysically()
        }

        private fun tryToInstallDescriptor(): Boolean {
            return if (install(index1, this.expected1)) install(index2, this.expected2) else false
        }

        fun install(index: Int, expected: E): Boolean {
            if (status.get() != UNDECIDED) return false
            while (true) {
                when (val cellState = array[index]) {
                    expected -> if (dcss(index, expected, this, status, UNDECIDED)) return true
                    this -> return true
                    is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> cellState.apply()
//                    expected -> if (array.compareAndSet(index, expected, this)) return true

                    else -> return false
                }
            }
        }

        private fun updateCellsLogically(success: Boolean) {
            if (success) status.compareAndSet(UNDECIDED, SUCCESS) else status.compareAndSet(UNDECIDED, FAILED)
        }

        private fun updateCellsPhysically() {
            val success = (status.get() == SUCCESS)

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
            array[index] = updateCellState
            true
        } else {
            false
        }
}