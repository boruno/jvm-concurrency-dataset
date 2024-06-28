@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
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
        val v = array[index]
        if (v is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            val status = v.status.get()!!
            return when (status) {
                SUCCESS -> if (v.index1 == index) v.update1 else v.update2
                FAILED, UNDECIDED -> if (v.index1 == index) v.expected1 else v.expected2
            } as E
        }
        return v as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
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
        private fun installDesc(): Int {
            val v1 = array[index1]
            if (v1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                v1.apply()
            }
            if (array.compareAndSet(index1, expected1, this)) {
                val v2 = array[index2]
                if (v2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    v2.apply()
                }
                if (array.compareAndSet(index2, expected2, this)) {
                    return 2
                }
                return 1
            }
            return 0
        }

        private fun updateStatus(installed: Int): Boolean {
            val new = when (installed) {
                2 -> SUCCESS
                else -> FAILED
            }
            return status.compareAndSet(UNDECIDED, new)
        }

        private fun updateCells(installed: Int) {
            when (installed) {
                2 -> {
                    array[index1] = update1
                    array[index2] = update2
                }
                1 -> array[index1] = expected1
            }
        }

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            if (status.get() == UNDECIDED) {
                val installed = installDesc()
                if (updateStatus(installed)) {
                    updateCells(installed)
                }
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}