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
        // TODO: Copy the implementation from `AtomicArrayWithCAS2Simplified`
        val node = array[index]
        return if (node is AtomicArrayWithCAS2AndImplementedDCSS<*>.CAS2Descriptor) {
            node.getValue(index) as E
        } else {
            node as E
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
            // TODO: Copy the implementation from `AtomicArrayWithCAS2Simplified`
            // TODO: and use `dcss(..)` to install the descriptor.
            val result = tryInstallDescriptor()
            updateStatusLogically(result)
            updateStatusPhysical()
        }

        private fun tryInstallDescriptor(): Boolean {
            return tryInstallDescriptorToCell(index1, expected1)
                    && tryInstallDescriptorToCell(index2, expected2)
        }

        private fun tryInstallDescriptorToCell(index: Int, expected: E): Boolean {
            while (true) {
                when (val cellState = array[index]) {
                    this -> return true
                    is AtomicArrayWithCAS2AndImplementedDCSS<*>.CAS2Descriptor -> {
                        cellState.apply()
                    }
                    expected -> {
                        if (dcss(index, expected, this, status, UNDECIDED)) {
                            return true
                        }
                    }
                    else -> return false
                }
            }
        }

        private fun updateStatusLogically(result: Boolean) {
            val newStatus = if (result) Status.SUCCESS else Status.FAILED
            status.compareAndSet(Status.UNDECIDED, newStatus)
        }

        private fun updateStatusPhysical() {
            if (status.get() == Status.SUCCESS) {
                array.compareAndSet(index1, this, update1)
                array.compareAndSet(index2, this, update2)
            } else {
                array.compareAndSet(index1, this, expected1)
                array.compareAndSet(index2, this, expected2)
            }
        }


        @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
        fun getValue(index: Int): Any = when (status.get()) {
            Status.UNDECIDED, Status.FAILED -> when (index) {
                index1 -> expected1
                else -> expected2
            }

            Status.SUCCESS -> when (index) {
                index1 -> update1
                else -> update2
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