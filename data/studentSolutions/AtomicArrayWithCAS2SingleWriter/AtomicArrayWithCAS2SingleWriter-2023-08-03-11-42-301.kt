//package day3

import AtomicArrayWithCAS2SingleWriter.Status.*
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
        // TODO: the cell can store CAS2Descriptor
        val cell = array[index].value
        return when (cell) {
            is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor -> {
                val updated = if (cell.index1 == index) cell.update1 else cell.update2
                val expected = if (cell.index1 == index) cell.expected1 else cell.expected2
                if (cell.status.value === SUCCESS) {
                    updated as E
                } else {
                    expected as E
                }
            }
            else -> cell as E
        }
//        return array[index].value as E
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
        public val index1: Int,
        public val expected1: E,
        public val update1: E,
        public val index2: Int,
        public val expected2: E,
        public val update2: E
    ) {
        public val status = atomic(UNDECIDED)

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            // TODO: In this task, only one thread can call cas2(..),
            // TODO: so cas2(..) calls cannot be executed concurrently.
            val old1 = array[index1].value
            val old2 = array[index2].value
//            if (array[index1].value != expected1 || array[index2].value != expected2) {
//                status.getAndSet(FAILED)
//                return
//            }
//            array[index1].getAndSet(this)
//            array[index2].getAndSet(this)
            if (!array[index1].compareAndSet(expected1, this)) {
                status.getAndSet(FAILED)
                return
            }
            if (!array[index2].compareAndSet(expected2, this)) {
                status.getAndSet(FAILED)
                // array[index1].getAndSet(old1)
                return
            }

            status.getAndSet(SUCCESS)

            array[index1].compareAndSet(this, update1)
            array[index2].compareAndSet(this, update2)
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}