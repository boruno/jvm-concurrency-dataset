@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

package day3

import java.util.concurrent.atomic.*
import day3.AtomicArrayWithCAS2.Status.*

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
        return when (val e = array[index]) {
            is AtomicArrayWithCAS2<*>.DCSSDescriptor -> {
                when (e.status.get()) {
                    SUCCESS -> (e.update as AtomicArrayWithCAS2<*>.CAS2Descriptor).observedValue(index)
                    else -> e.expected
                }
            }
            is AtomicArrayWithCAS2<*>.CAS2Descriptor -> {
                when (e.status.get()) {
                    SUCCESS -> if (index == e.index1) e.update1 else e.update2
                    else -> if (index == e.index1) e.expected1 else e.expected2
                }
            }
            else -> e
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

        fun observedValue(index: Int): E = when (status.get()) {
            SUCCESS -> if (index == index1) update1 else update2
            else -> if (index == index1) expected1 else expected2
        }

        fun apply() {
            // TODO: Copy the implementation from `AtomicArrayWithCAS2Simplified`
            // TODO: and use `dcss(..)` to install the descriptor.
            val success = install()
            status.compareAndSet(UNDECIDED, if (success) SUCCESS else FAILED)
            uninstall()
        }

        private fun uninstall() {
            val succeeded = status.get() == SUCCESS
            array.compareAndSet(index1, this, if (succeeded) update1 else expected1)
            array.compareAndSet(index2, this, if (succeeded) update2 else expected2)
        }

        private fun install(): Boolean {
            if (!installInto(index1, expected1)) return false
            return installInto(index2, expected2)
        }

        private fun installInto(index: Int, expected: E): Boolean {
            while (true) {
                if (status.get() != UNDECIDED) return false
                when (val cell = array[index]) {
                    this -> return true
                    is AtomicArrayWithCAS2<*>.CAS2Descriptor -> cell.apply()
                    is AtomicArrayWithCAS2<*>.DCSSDescriptor -> cell.apply()
                    expected -> if (dcss(index, expected, this, status, UNDECIDED)) return true
                    else -> return false
                }
            }
        }
    }
    
    inner class DCSSDescriptor(
        val index: Int,
        val expected: E,
        val update: Any,
        val statusReference: AtomicReference<Status>,
        val expectedStatus: Status,
    ) {
        val status = AtomicReference(UNDECIDED)

        fun apply() {
            // TODO: Copy the implementation from `AtomicArrayWithCAS2Simplified`
            // TODO: and use `dcss(..)` to install the descriptor.
            val success = installInto(index, expected)
            status.compareAndSet(UNDECIDED, if (success) SUCCESS else FAILED)
            uninstall()
        }

        private fun uninstall() {
            val succeeded = status.get() == SUCCESS && statusReference.get() == expectedStatus
            array.compareAndSet(index, this, if (succeeded) update else expected)
        }

        private fun installInto(index: Int, expected: E): Boolean {
            while (true) {
                if (status.get() != UNDECIDED) return false
                when (val cell = array[index]) {
                    this -> return true
                    is AtomicArrayWithCAS2<*>.DCSSDescriptor -> cell.apply()
                    is AtomicArrayWithCAS2<*>.CAS2Descriptor -> cell.apply()
                    expected -> if (array.compareAndSet(index, expected, this)) return true
                    else -> return false
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
    ): Boolean {
        val descriptor = DCSSDescriptor(index, expectedCellState as E, updateCellState as E, statusReference as AtomicReference<Status>, expectedStatus as Status)
        descriptor.apply()
        return descriptor.status.get() == SUCCESS
    }
        
}