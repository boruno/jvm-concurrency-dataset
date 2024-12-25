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
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?, update2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: Store a CAS2 descriptor in array[index1].
        if (array[index1] != expected1 || array[index2] != expected2) return false
        array[index1] = update1
        array[index2] = update2
        return true
    }

    // DCSS: if (currentB == expectedB) currentB = updateB else
    inner class DcssDescriptor(
        val index: Int,
        val expectedB: E,
        val updateB: E,
        val expectedCAS2Status: Status,
    ) {
        val status = AtomicReference(UNDECIDED)

        fun apply() {
            val success = installDescriptor()
            updateStatus(success)
            updateCells()
        }

        private fun installDescriptor(): Boolean {
            // CAS returns true => there IS expectedB
            return array.compareAndSet(index, expectedB, this)
        }

        private fun updateStatus(success: Boolean) {
            status.compareAndSet(UNDECIDED, if (success) SUCCESS else FAILED)
        }

        private fun updateCells() {
            val success = status.get() == SUCCESS
            if (success && expectedCAS2Status == array[index]?.fromDescriptorOrValue { status }) {
                array.compareAndSet(index, this, updateB)
            }
            else {
                array.compareAndSet(index, this, expectedB)
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
            } else {
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
            while (true) {
                if (status.get() != UNDECIDED) {
                    return false
                }
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

                    is AtomicArrayWithCAS2<*>.CAS2Descriptor -> {
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
        private inline fun Any.fromDescriptorOrValue(fromDescriptor: (AtomicArrayWithCAS2<*>.CAS2Descriptor) -> Any) =
            if (this is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                fromDescriptor(this)
            } else this
    }
}