@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import java.util.concurrent.atomic.*
import kotlin.math.exp


// This implementation never stores `null` values.
class AtomicArrayWithCAS2Simplified<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    fun get(index: Int): Any? {
        // TODO: the cell can store CAS2Descriptor
//        return array[index] as E
        val state = array[index]
        return if (state is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            val status = state.status.get()
            if (index == state.index1) {
                return if (status == SUCCESS) state.update1 else state.expected1
            }
            if (status == SUCCESS) state.update2 else state.expected2
        } else state

        //            if (state.status.get() == SUCCESS) {
//                return if (index == state.index1) state.update1 else state.update2
//            } else {
//
//            } }else {
//                state
//            }
//        }
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
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.

            val success = tryToInstallDescriptor()
            updateCellsLogically(success)
            updateCellsPhysically()
        }

        private fun tryToInstallDescriptor(): Boolean {
            return if (install(1, this.expected1)) install(2, this.expected2) else false
        }

        fun install(index: Int, expected: E): Boolean {
            if (status.get() != UNDECIDED) return false
            while (true) {
                val cellState = array[index]
                when (cellState) {
                    this -> {
                        return true
                    }
                    is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
                        cellState.apply()
                        return false
                    }
                    expected -> {
                        if (array.compareAndSet(index, expected, this)) return true
                    }
                    else -> {
                        return false
                    }
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
}