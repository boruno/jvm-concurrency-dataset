@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import java.util.concurrent.atomic.*
import AtomicArrayWithCAS2.Status.*

// This implementation never stores `null` values.
class AtomicArrayWithCAS2<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    fun get(index: Int): E? {
        // TODO: the cell can store a descriptor
        return when (val cell = array[index]) {
            is AtomicArrayWithCAS2<*>.CAS2Descriptor -> {
                when (cell.status.get()) {
                    SUCCESS -> cell.getUpdateValue(index)
                    else -> cell.getExpectedValue(index)
                }
            }

            is AtomicArrayWithCAS2<*>.DCSSDescriptor -> {
                when (cell.statusReference.get()) {
                    SUCCESS -> cell.update.getUpdateValue(index)
                    else -> cell.expected
                }
            }

            else -> cell
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
        // TODO: this implementation is not linearizable,
        // TODO: Store a CAS2 descriptor in array[index1].
//        if (array[index1] != expected1 || array[index2] != expected2) return false
//        array[index1] = update1
//        array[index2] = update2
//        return true
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

        fun getExpectedValue(index: Int) = if (index == index1) expected1 else expected2
        fun getUpdateValue(index: Int) = if (index == index1) update1 else update2

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.

            val success = install()
            updateStatus(success)
            updatePhysically()
        }

        private fun install(): Boolean {
            if (!install(index1, expected1)) return false
            return install(index2, expected2)
        }

        private fun install(index: Int, expected: E): Boolean {
            while (true) {
                if (status.get() != UNDECIDED) return false
                when (val cell = array[index]) {
                    this -> return true

                    is AtomicArrayWithCAS2<*>.CAS2Descriptor -> {
                        cell.apply()
                    }

//                    is AtomicArrayWithCAS2<*>.DCSSDescriptor -> {
//                        cell.apply()
//                    }

                    expected -> {
                        return if (dcss(index, expected, this, status, UNDECIDED)) true else continue
                    }

                    else -> return false
                }
            }
        }

        private fun updateStatus(success: Boolean) {
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

    fun dcss(
        index: Int,
        expectedCellState: Any?,
        updateCellState: CAS2Descriptor,
        statusReference: AtomicReference<Status>,
        expectedStatus: Status
    ): Boolean {
        val descriptor = DCSSDescriptor(
            index,
            expectedCellState,
            updateCellState,
            statusReference,
            expectedStatus
        )
        descriptor.apply()
        return descriptor.dcssStatus.get() === SUCCESS
    }

    inner class DCSSDescriptor(
        val index: Int,
        val expected: Any?,
        val update: CAS2Descriptor,
        val statusReference: AtomicReference<Status>,
        val expectedStatus: Status,
    ) {
        val dcssStatus = AtomicReference(UNDECIDED)

        fun apply() {
            val ok = install()
            updateStatus(ok)
            updatePhysically()
        }

        private fun install(): Boolean {
            return array.compareAndSet(index, expected, this)
        }

        private fun updateStatus(ok: Boolean) {
            dcssStatus.compareAndSet(UNDECIDED, if (ok) SUCCESS else FAILED)
        }

        private fun updatePhysically() {
            if (dcssStatus.get() == SUCCESS && statusReference.get() == expectedStatus)
                array.compareAndSet(index, this, update)
            else
                array.compareAndSet(index, this, expected)
        }
    }
}