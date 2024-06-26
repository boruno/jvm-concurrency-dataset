@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

package day3

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
        val maybeDescriptor = array[index]!!
        return maybeDescriptor.fromDescriptorOrValue {
            if (it.status.get() === SUCCESS) {
                if (index == it.index1) it.update1 else it.update2
            }
            else {
                if (index == it.index1) it.expected1 else it.expected2
            }
        } as E
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
            val success = installDescriptor()
            applyNoInstall(success)
        }

        fun applyNoInstall(success: Boolean) {
            updateStatus(success)
            updateCells()
        }

        private fun updateCells() {
            val success = status.get() == SUCCESS
            if (success) {
                array.compareAndSet(index1, this, update1)
                array.compareAndSet(index2, this, update2)
            }
            else {
                array.compareAndSet(index1, this, expected1)
                array.compareAndSet(index2, this, expected2)
            }
        }

        private fun updateStatus(success: Boolean) {
            status.compareAndSet(UNDECIDED, if (success) SUCCESS else FAILED)
        }

        private fun installDescriptor(): Boolean {
            if (!install(index1, expected1)) return false
            return install(index2, expected2)
        }

        private fun install(index: Int, expected: E): Boolean {
            if (status.get() != UNDECIDED) {
                return false
            }
            while (true) {
                val cellState = array[index]
                when (cellState) {
                    expected -> {
                        if (dcss(index, expected, this, status, UNDECIDED)) {
                            return true
                        }
                        continue
                    }
                    this -> {
                        return true
                    }
                    is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
                        cellState.apply()
                    }
                    else -> {
                        return false
                    }
                }
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

    companion object {
        private inline fun Any.fromDescriptorOrValue(fromDescriptor: (AtomicArrayWithCAS2AndImplementedDCSS<*>.CAS2Descriptor) -> Any) =
            if (this is AtomicArrayWithCAS2AndImplementedDCSS<*>.CAS2Descriptor) {
                fromDescriptor(this)
            } else this
    }
}