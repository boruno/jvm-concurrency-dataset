//package day3

import AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.*


// This implementation never stores `null` values.
class AtomicArrayWithCAS2Simplified<E : Any>(size: Int, initialValue: E) {
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
            is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor ->
                when (value.status.value) {
                    SUCCESS -> value.updated(index)
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
            index1Raw = index1, expected1 = expected1, update1 = update1,
            index2Raw = index2, expected2 = expected2, update2 = update2
        )
        descriptor.apply()
        return descriptor.status.value === SUCCESS
    }

    inner class CAS2Descriptor(
        index1Raw: Int,
        private val expected1: E,
        private val update1: E,
        index2Raw: Int,
        private val expected2: E,
        private val update2: E
    ) {
        val status = atomic(UNDECIDED)

        private val index1 = minOf(index1Raw, index2Raw)
        private val index2 = maxOf(index1Raw, index2Raw)

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
            while (true) {
                val value1 = array[index1].value
                if (value1 != this && value1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    value1.apply()
                    continue
                }

                val value2 = array[index2].value
                if (value2 != this && value2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    value2.apply()
                    continue
                }

                if (value1 != expected1 && value1 != this || value2 != expected2 && value2 != this) {
                    status.compareAndSet(UNDECIDED, FAILED)
                    rollback()
                    return
                }
                // now value1 == expected1 and value2 == expected2

                if (status.value == UNDECIDED) {
                    if (value1 != this && !array[index1].compareAndSet(expected1, this)) {
                        continue
                    }
                    if (value2 != this && !array[index2].compareAndSet(expected2, this)) {
                        continue
                    }
                    if (status.compareAndSet(UNDECIDED, SUCCESS)) {
                        array[index1].compareAndSet(this, update1)
                        array[index2].compareAndSet(this, update2)
                    }
                }


                return
            }
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