@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

package day3

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

    fun get(index: Int): E? {
        return when (val cellState = array[index]) {
            is AtomicArrayWithCAS2<*>.CAS2Descriptor -> (cellState as AtomicArrayWithCAS2<E>.CAS2Descriptor).getValue(
                index
            )

            is AtomicArrayWithCAS2<*>.DcssDescriptor -> (cellState as AtomicArrayWithCAS2<E>.DcssDescriptor).getValue()
            else -> cellState as E
        }
    }

    fun cas2(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?, update2: E?
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
        val expected1: E?,
        val update1: E?,
        val index2: Int,
        val expected2: E?,
        val update2: E?,
    ) {
        val status = AtomicReference(Status.UNDECIDED)

        fun apply() {
            val installed = installDescriptor()
            applyLogically(installed)
            applyPhysically()
        }

        fun getValue(index: Int): E? {
            return when (status.get()!!) {
                Status.UNDECIDED, Status.FAILED -> when (index) {
                    index1 -> expected1
                    index2 -> expected2
                    else -> throw RuntimeException()
                }

                Status.SUCCESS -> when (index) {
                    index1 -> update1
                    index2 -> update2
                    else -> throw RuntimeException()
                }
            }
        }

        private fun installDescriptor(): Boolean {
            if (!installDescriptor(index1, expected1)) return false
            return installDescriptor(index2, expected2)
        }

        private fun installDescriptor(index: Int, expected: E?): Boolean {
            while (true) {
                if (status.get() != Status.UNDECIDED) return false
                when (val cellState = array[index]) {
                    expected -> {
                        if (dcss(index, expected, this, status, Status.UNDECIDED)) {
                            return true
                        }
                    }

                    this -> {
                        return true
                    }

                    is AtomicArrayWithCAS2<*>.CAS2Descriptor -> {
                        cellState as AtomicArrayWithCAS2<E>.CAS2Descriptor
                        cellState.apply()
                    }

                    else -> {
                        return false
                    }
                }
            }
        }

        private fun applyLogically(installed: Boolean): Boolean {
            return when {
                installed -> status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
                else -> status.compareAndSet(Status.UNDECIDED, Status.FAILED)
            }
        }

        private fun applyPhysically() {
            if (status.get() == Status.SUCCESS) {
                array.compareAndSet(index1, this, update1)
                array.compareAndSet(index2, this, update2)
            } else {
                array.compareAndSet(index1, this, expected1)
                array.compareAndSet(index2, this, expected2)
            }
        }
    }

    inner class DcssDescriptor(
        private val index: Int,
        private val expectedB: E?,
        private val updateB: AtomicArrayWithCAS2<E>.CAS2Descriptor?,
        private val statusReference: AtomicReference<*>,
        private val expectedCas2Status: Any?,
    ) {
        val status = AtomicReference(Status.UNDECIDED)

        fun getValue(): E? {
            return when (status.get()!!) {
                Status.UNDECIDED, Status.FAILED -> expectedB
                Status.SUCCESS -> (updateB as AtomicArrayWithCAS2<E>.CAS2Descriptor).getValue(index)
            }
        }

        fun apply() {
            val installed = installDescriptor()
            applyLogically(installed)
            applyPhysically()
        }

        private fun installDescriptor(): Boolean {
            return array.compareAndSet(index, expectedB, this)
        }

        private fun applyLogically(installed: Boolean) {
            when {
                installed -> status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
                else -> status.compareAndSet(Status.UNDECIDED, Status.FAILED)
            }
        }

        private fun applyPhysically() {
            if (status.get() == Status.SUCCESS) {
                array.compareAndSet(index, this, updateB)
            } else {
                array.compareAndSet(index, this, expectedB)
            }
        }
    }

    fun dcss(
        index: Int,
        expectedCellState: E?,
        updateCellState: AtomicArrayWithCAS2<E>.CAS2Descriptor?,
        statusReference: AtomicReference<*>,
        expectedStatus: Any?,
    ): Boolean {
        if (statusReference.get() != expectedCellState) return false
        val descriptor = DcssDescriptor(index, expectedCellState, updateCellState, statusReference, expectedStatus)
        descriptor.apply()
        return descriptor.status.get() === Status.SUCCESS
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}