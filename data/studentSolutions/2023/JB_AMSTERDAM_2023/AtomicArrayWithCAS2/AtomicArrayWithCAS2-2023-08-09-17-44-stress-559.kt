@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

package day3

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
        val elem = array.get(index)
        return getElementAtIndex(elem, index)
    }

    private fun getElementAtIndex(elem: Any?, index: Int): E {
        if (elem is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
            if (elem.status.get() != Status.SUCCESS) {
                if (elem.index1 == index) return elem.expected1 as E
                return elem.expected2 as E
            }
            if (elem.index1 == index) return elem.update1 as E
            return elem.update2 as E
        } else if (elem is AtomicArrayWithCAS2<*>.DcssDescriptior) {
            if (elem.status.get() != Status.SUCCESS) return getElementAtIndex(elem.expectedCellState, index)
            return getElementAtIndex(elem.updateCellState, index)
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
        return descriptor.status.get() === Status.SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = AtomicReference(Status.UNDECIDED)

        private fun install() : Boolean {
            if (!install(index1, expected1)) return false
            return install(index2, expected2)
        }

        private fun install(idx: Int, exp: Any): Boolean {
            while (true) {
                if (status.get() != Status.UNDECIDED) return false
                when (val cellState = array.get(idx)) {
                    this -> return true
                    is AtomicArrayWithCAS2<*>.CAS2Descriptor -> cellState.apply()
                    is AtomicArrayWithCAS2<*>.DcssDescriptior -> return false//cellState.apply()
                    exp -> {
                        if (dcss(idx, exp, this, status, Status.UNDECIDED)) {
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

    inner class DcssDescriptior(
        val index: Int,
        val expectedCellState: Any?,
        val updateCellState: Any?,
        val statusReference: AtomicReference<*>,
        val expectedStatus: Any?
    ) {
        val status = AtomicReference(Status.UNDECIDED)

        private fun install(): Boolean {
            return install(index, expectedCellState)
        }

        private fun install(idx: Int, exp: Any?): Boolean {
            while (true) {
                if (status.get() != Status.UNDECIDED) return false
                when (val cellState = array.get(idx)) {
                    this -> return true
                    is AtomicArrayWithCAS2<*>.CAS2Descriptor -> cellState.apply()
                    is AtomicArrayWithCAS2<*>.DcssDescriptior -> cellState.apply()
                    exp -> {
                        if (array.compareAndSet(idx, exp, this)) {
                            return statusReference.get() == expectedStatus
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
                    array.compareAndSet(index, this, expectedCellState)
                }
                Status.SUCCESS -> {
                    // apply original values
                    array.compareAndSet(index, this, updateCellState)
                }
            }
        }

        fun apply() {
            updateStatus(install())
            updateCell()
        }
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
        val dcss = DcssDescriptior(index, expectedCellState, updateCellState, statusReference, expectedStatus)
        dcss.apply()
        return dcss.status.get() == Status.SUCCESS
    }
}