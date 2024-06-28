@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import java.util.concurrent.atomic.*


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
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = if (index1 <= index2) {
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

            // 1. TRY TO INSTALL DESCRIPTOR
            tryToInstallDescriptor()

            // 2. UPDATE CELLS LOGICALLY
            updateCellsLogically()

            // 3. UPDATE CELLS PHYSICALLY
            updateCellsPhysically()

            return
        }

        private fun tryToInstallDescriptor() {
            // help another descriptor if there is one
            val firstElem = array.get(index1)
            if (firstElem is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) firstElem.apply()

            // set descriptor to the first cell first
            if (!array.compareAndSet(index1, expected1, this)) {
                if (status.compareAndSet(UNDECIDED, FAILED)) return
                if (status.get() == SUCCESS) {
                    updateCellsPhysically()
                    return
                }
            }

            // set descriptor to the second cell
            if (!array.compareAndSet(index2, expected2, this)) {
                if (status.compareAndSet(UNDECIDED, FAILED)) {
                    array.set(index1, expected1)
                    return
                }
                if (status.get() == SUCCESS) {
                    updateCellsPhysically()
                    return
                }
            }
        }

        private fun updateCellsLogically() {
            if (status.compareAndSet(UNDECIDED, SUCCESS)) return
        }

        private fun updateCellsPhysically() {
            if (array.compareAndSet(index1, this, update1)) {
                array.set(index2, update2)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}