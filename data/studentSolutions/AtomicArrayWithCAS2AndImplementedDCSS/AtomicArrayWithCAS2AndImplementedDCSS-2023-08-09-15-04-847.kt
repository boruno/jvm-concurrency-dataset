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

    fun get(index: Int): E {
        val elem = array.get(index)
        if (elem is AtomicArrayWithCAS2AndImplementedDCSS<*>.CAS2Descriptor) {
            if (elem.status.get() != Status.SUCCESS) {
                if (elem.index1 == index) return elem.expected1 as E
                return elem.expected2 as E
            }
            if (elem.index1 == index) return elem.update1 as E
            return elem.update2 as E
        }
        return elem as E
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

        private fun install() : Boolean {
            if (!install(index1, expected1)) return false
            return install(index2, expected2)
        }

        private fun install(idx: Int, exp: Any): Boolean {
            if (status.get() != Status.UNDECIDED) return false
            while (true) {
                when (val cellState = array.get(idx)) {
                    this -> return true
                    is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> cellState.apply()
                    exp -> {
                        if (dcss(idx, exp, this, status, UNDECIDED)) {
                            return true
                        }
                    }
                    else -> return false
                }
            }
        }

        private fun updateStatus(stat: Boolean) {
            status.compareAndSet(Status.UNDECIDED, if (stat) Status.SUCCESS else Status.FAILED)
        }

        private fun updateCell() {
            when(status.get()) {
                Status.UNDECIDED, null -> throw Exception("")
                Status.FAILED -> {
                    // roll back the values
                    array.compareAndSet(index1, this, expected1)
                    array.compareAndSet(index2, this, expected2)
                }
                Status.SUCCESS -> {
                    // apply original values
                    array.compareAndSet(index1, this, update1)
                    array.compareAndSet(index2, this, update2)
                }
            }
        }

        fun apply() {
            updateStatus(install())
            updateCell()
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