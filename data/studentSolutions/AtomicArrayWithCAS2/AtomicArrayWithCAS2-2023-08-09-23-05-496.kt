@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

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

    fun get(index: Int): E? {
        // TODO: the cell can store a descriptor
        val value = array[index]
        return when (value) {
            is AtomicArrayWithCAS2<*>.CAS2Descriptor -> {
                valueFromCas2(value, index)
            }
            is AtomicArrayWithCAS2<*>.DcssDescriptor -> {
                valueFromCas2(
                    if (value.expected is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                        value.expected
                    } else {
                        value.update as AtomicArrayWithCAS2<*>.CAS2Descriptor
                    },
                    index
                )
            }
            else -> {
                value as E
            }
        }
    }

    private fun valueFromCas2(
        value: AtomicArrayWithCAS2<*>.CAS2Descriptor,
        index: Int
    ): E = when (value.status.get()) {
        UNDECIDED, FAILED -> value.getExpectedForIndex(index) as E
        SUCCESS -> value.getUpdateForIndex(index) as E
        else -> throw IllegalStateException()
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: Store a CAS2 descriptor in array[index1].
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
            // TODO: Copy the implementation from `AtomicArrayWithCAS2Simplified`
            // TODO: and use `dcss(..)` to install the descriptor.
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
                when(val cellState = array[index]) {
                    this -> return true
                    is AtomicArrayWithCAS2<*>.CAS2Descriptor -> cellState.apply()
                    is AtomicArrayWithCAS2<*>.DcssDescriptor -> cellState.apply(index)
                    expected -> {
                        if (dcss(index, expected, this, status, UNDECIDED)) return true
                    }
                    else -> {
                        // Unexpected value
                        return false
                    }
                }
            }
        }

        private fun updateStatus(success: Boolean) {
            status.compareAndSet(UNDECIDED, if (success) SUCCESS else FAILED)
        }

        private fun updatePhysically() {
            val success = status.get() == SUCCESS
            if (success) {
                dcss(index1, this, update1, status, SUCCESS)
                dcss(index2, this, update2, status, SUCCESS)
            } else {
                dcss(index1, this, expected1, status, FAILED)
                dcss(index2, this, expected2, status, FAILED)
            }
        }

        fun getExpectedForIndex(index: Int): E  {
            return when (index) {
                index1 -> expected1
                index2 -> expected2
                else -> throw IllegalArgumentException("Current CAS2 operation does not know $index")
            }
        }

        fun getUpdateForIndex(index: Int): E  {
            return when (index) {
                index1 -> update1
                index2 -> update2
                else -> throw IllegalArgumentException("Current CAS2 operation does not know $index")
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }

    inner class DcssDescriptor(
        val expected: Any?,
        val update: Any?,
        val status: AtomicReference<*>,
        val expectedStatus: Status
    ) {
        fun apply(index: Int): Boolean {
            return if (status.get() == expectedStatus) {
                array.compareAndSet(index, this, update)
            } else {
                false
            }
        }
    }

    fun dcss(
        index: Int,
        expectedCellState: Any?,
        updateCellState: Any?,
        statusReference: AtomicReference<*>,
        expectedStatus: Status
    ): Boolean {
        val dcssDescriptor = DcssDescriptor(expectedCellState, updateCellState, statusReference, expectedStatus)
        while (true) {
            when (val cell = array[index]) {
                is AtomicArrayWithCAS2<*>.DcssDescriptor -> if (cell.apply(index)) return true
                else ->  {
                    if (statusReference.get() != expectedStatus) return false
                    if (!array.compareAndSet(index, expectedCellState, dcssDescriptor)) return false
                }
            }
        }
    }
}