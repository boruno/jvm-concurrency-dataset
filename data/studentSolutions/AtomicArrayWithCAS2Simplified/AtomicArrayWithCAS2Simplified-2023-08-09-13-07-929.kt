@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import AtomicArrayWithCAS2Simplified.Status.*
import java.util.concurrent.atomic.*


// This implementation never stores `null` values.
class AtomicArrayWithCAS2Simplified<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    fun get(index: Int): E {
        // TODO: the cell can store CAS2Descriptor
        val result = array.get(index)

        return when (result) {
            is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> if (result.status.get() == SUCCESS) {
                when (index) {
                    result.index1 -> result.update1
                    result.index2 -> result.update2
                    else -> error("Unexpected index $index")
                }
            } else {
                when (index) {
                    result.index1 -> result.expected1
                    result.index2 -> result.expected2
                    else -> error("Unexpected index $index")
                }
            }

            else -> result
        } as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val (value1, value2) = if (index1 < index2) {
            Triple(index1, expected1, update1) to Triple(index2, expected2, update2)
        } else {
             Triple(index2, expected2, update2) to Triple(index1, expected1, update1)
        }

        val (index1, expected1, update1) = value1
        val (index2, expected2, update2) = value2

        val descriptor = CAS2Descriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2, update2 = update2
        )
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
            if (status.get() != UNDECIDED) return
            var isSuccess = install()

            if (!updateStatus(isSuccess)) return
            updateCells(isSuccess)
        }

        fun install(): Boolean {
            if (!array.compareAndSet(index1, expected1, this)) {
                when (val other = array.get(index1)) {
                    this -> {}
                    is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
                        other.apply()
                        return install()
                    }
                    else -> return false
                }
            }

            if (!array.compareAndSet(index2, expected2, this)) {
                when (val other = array.get(index2)) {
                    this -> {}
                    is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
                        other.apply()
                        return install()
                    }
                    else -> return false
                }
            }

            return true
        }

        fun updateStatus(isSuccess: Boolean): Boolean {
            return status.compareAndSet(UNDECIDED, if (isSuccess) SUCCESS else FAILED)
        }

        fun updateCells(isSuccess: Boolean) {
            array.compareAndSet(index1, this, if (isSuccess) update1 else expected1)
            array.compareAndSet(index2, this, if (isSuccess) update2 else expected2)
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}