//package day3

import day3.AtomicArrayWithCAS2SingleWriter.Status.*
import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class AtomicArrayWithCAS2SingleWriter<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E {
        val cell = array[index].value
        return ((cell as? AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor)?.read(index) ?: cell) as E
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
        return descriptor.status.value === SUCCESS
    }

    inner class CAS2Descriptor(
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E,
        private val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            val idx1 = array[index1].compareAndSet(expected1, this)
            val idx2 = array[index2].compareAndSet(expected2, this)

            if (idx1 && idx2) {
                status.compareAndSet(UNDECIDED, SUCCESS)
            }

            if (status.value === SUCCESS) {
                array[index1].compareAndSet(expected1, update1)
                array[index2].compareAndSet(expected2, update2)
            }
        }

        fun read(index: Int): E? {
            return when (index) {
                index1 -> getVal(1)
                index2 -> getVal(2)
                else -> null
            }
        }

        private fun getVal(i: Int) = if (status.value === SUCCESS) {
            if (i == 1) update1 else update2
        } else {
            if (i == 1) expected1 else expected2
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}