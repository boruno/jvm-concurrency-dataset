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
        private val indexMin = minOf(index1, index2)
        private val indexMax = maxOf(index1, index2)
        private val expectedMin = if (indexMin == index1) expected1 else expected2
        private val expectedMax = if (indexMax == index1) expected1 else expected2
        private val updateMin = if (indexMin == index1) update1 else update2
        private val updateMax = if (indexMax == index1) update1 else update2

        val status = AtomicReference(UNDECIDED)

        private fun helpWith(index: Int) {
            while (true) {
                val v = array[index]
                if (v is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    v.apply()
                } else {
                    break
                }
            }
        }

        private fun installDesc(): Int {
//            helpWith(indexMin)
            if (array.compareAndSet(indexMin, expectedMin, this)) {
//                helpWith(indexMax)
                if (array.compareAndSet(indexMax, expectedMax, this)) {
                    return 2
                }
                return 1
            }
            return 0
        }

        private fun updateStatusLogicalUpdate(installed: Int): Boolean {
            val new = when (installed) {
                2 -> SUCCESS
                else -> FAILED
            }
            return status.compareAndSet(UNDECIDED, new)
        }

        private fun updateCellsPhysicalUpdate() {
            when (status.get()!!) {
                SUCCESS -> {
                    array.compareAndSet(indexMax, this, updateMax)
                    array.compareAndSet(indexMin, this, updateMin)
                }
                FAILED -> {
                    array.compareAndSet(indexMin, this, expectedMin)
                }
                UNDECIDED -> {}
            }
        }

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            if (status.get() == UNDECIDED) {
                val installed = installDesc()
                updateStatusLogicalUpdate(installed)
            }
            updateCellsPhysicalUpdate()
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}