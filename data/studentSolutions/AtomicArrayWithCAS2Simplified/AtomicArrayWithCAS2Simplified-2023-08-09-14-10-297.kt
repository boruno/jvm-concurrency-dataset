@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import AtomicArrayWithCAS2Simplified.Status.FAILED
import AtomicArrayWithCAS2Simplified.Status.SUCCESS
import AtomicArrayWithCAS2Simplified.Status.UNDECIDED
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray


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
        val e = array[index] as E
        return when (e) {
            is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
                if (e.status.get() == Status.UNDECIDED || e.status.get() == Status.FAILED) {
                    if (e.index1 == index) e.expected1 else e.expected2
                } else {
                    if (e.index1 == index) e.update1 else e.update2
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
                index1 = index2, expected1 = expected1, update1 = update1,
                index2 = index1, expected2 = expected2, update2 = update2
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
            val success = install()
            updateStatus(success)
            updatePhysically()
        }

        fun install(): Boolean {
            if (!install(index1, expected1)) return false
            return install(index2, expected2)
        }

        fun install(index: Int, expected: E): Boolean {
            if (status.get() != UNDECIDED) return false
            while (true) {
                val cellState = array.get(index)
                when (cellState) {
                    this -> {
                        return true
                    }
                    is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
                        cellState.apply()
                    }
                    expected -> {
                        if (array.compareAndSet(index, expected, this)) {
                            return true
                        }
                    }
                    else -> { //unexpected value
                        return false
                    }
                }
            }
        }

        fun updateStatus(success: Boolean) {
            status.compareAndSet(UNDECIDED, if (success) SUCCESS else FAILED)
        }

        fun updatePhysically() {
            val success = status.get() == SUCCESS
            if (success) {
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
}