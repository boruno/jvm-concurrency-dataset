@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

package day3

import day3.AtomicArrayWithCAS2.Status.FAILED
import day3.AtomicArrayWithCAS2.Status.SUCCESS
import day3.AtomicArrayWithCAS2.Status.UNDECIDED
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray

// This implementation never stores `null` values.
class AtomicArrayWithCAS2<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    fun get(index: Int): E = when (val element = array[index]) {
        is AtomicArrayWithCAS2<*>.CAS2Descriptor -> {
            if (element.status.get() === SUCCESS) {
                if (element.index1 == index) {
                    element.update1 as E
                } else {
                    element.update2 as E
                }
            } else {
                if (element.index1 == index) {
                    element.expected1 as E
                } else {
                    element.expected2 as E
                }
            }
        }

        is AtomicArrayWithCAS2<*>.DCSSDescriptor -> {
            if (element.statusReference.get() === element.expectedCellState) {
                element.updateCellState as E
            } else {
                element.expectedCellState as E
            }
        }

        else -> element as E
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
            val result = install()
            updateStatus(result)
            updateCells()
        }

        private fun install() = install(index1, expected1) && install(index2, expected2)

        private fun install(index: Int, expected: E): Boolean {
            while (true) {
                val curStatus = status.get()
                if (curStatus !== UNDECIDED) {
                    return curStatus == SUCCESS
                }
                when (val element = array[index]) {
                    this -> return true
                    expected -> if (DCSSDescriptor(index, expected, this, status, UNDECIDED).dcss()) return true
                    is AtomicArrayWithCAS2<*>.CAS2Descriptor -> element.apply()
                    else -> return false
                }
            }
        }

        private fun updateStatus(result: Boolean) {
            status.compareAndSet(UNDECIDED, if (result) SUCCESS else FAILED)
        }

        private fun updateCells() {
            val result = status.get() == SUCCESS
            array.compareAndSet(index1, this, if (result) update1 else expected1)
            array.compareAndSet(index2, this, if (result) update2 else expected2)
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }

    inner class DCSSDescriptor(
        val index: Int,
        val expectedCellState: Any?,
        val updateCellState: Any?,
        val statusReference: AtomicReference<*>,
        val expectedStatus: Any?
    ) {
        fun dcss(): Boolean {
            if (statusReference.get() != expectedStatus) {
                return false
            }
            while (true) {
                when (val element = array.get(index)) {
                    expectedCellState -> {
                        if (!array.compareAndSet(index, expectedCellState, this)) {
                            return false
                        }
                    }

                    this -> {
                        if (statusReference.get() != expectedStatus) {
                            array.compareAndSet(index, element, expectedCellState)
                            return false
                        }
                        return array.compareAndSet(index, element, updateCellState)
                    }

                    is AtomicArrayWithCAS2<*>.DCSSDescriptor -> {
                        if (element.statusReference.get() != element.expectedStatus) {
                            array.compareAndSet(index, element, element.expectedCellState)
                            continue
                        }
                        array.compareAndSet(index, element, element.updateCellState)
                    }

                    else -> return false
                }
            }
        }
    }
}