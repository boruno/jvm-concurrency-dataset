package day3

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
        val value = array[index].value
        return when (value) {
            is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor ->
                when (value.status.value) {
                    SUCCESS -> value.expected(index)
                    FAILED -> value.expected(index)
                    UNDECIDED -> value.expected(index)
                } as E
            else -> value as E
        }
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

        fun expected(index: Int): E {
            return when (index) {
                index1 -> expected1
                index2 -> expected2
                else -> error("wrong index $index, expected $index1 or $index2")
            }
        }

        fun updated(index: Int): E {
            return when (index) {
                index1 -> update1
                index2 -> update2
                else -> error("wrong index $index, expected $index1 or $index2")
            }
        }

        fun apply() {
            if (!array[index1].compareAndSet(expected1, this)) {
                status.value = FAILED
                return
//                rollback()
            }
            if (!array[index2].compareAndSet(expected2, this)) {
                status.value = FAILED
                rollback()
                return
            }

            status.value = SUCCESS
            array[index1].compareAndSet(this, update1)
            array[index2].compareAndSet(this, update2)
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            // TODO: In this task, only one thread can call cas2(..),
            // TODO: so cas2(..) calls cannot be executed concurrently.
        }

        private fun rollback() {
            array[index1].compareAndSet(this, expected1)
            array[index2].compareAndSet(this, expected2)
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}