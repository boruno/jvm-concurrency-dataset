//package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
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
        val descriptor = CAS2DescriptorImpl(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2, update2 = update2
        )
        descriptor.apply()
        return descriptor.status.value === SUCCESS
    }

    fun CAS2DescriptorImpl(
        index1: Int,
        expected1: E,
        update1: E,
        index2: Int,
        expected2: E,
        update2: E
    ): CAS2Descriptor {
        return if (index1 < index2) {
            CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        } else {
            CAS2Descriptor(index2, expected2, update2, index1, expected1, update1)
        }
    }

    inner class CAS2Descriptor constructor(
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
            while (true) {
                if (!array[index1].compareAndSet(expected1, this)) {
                    val value = array[index1].value
                    if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                        if (value != this) {
                            value.apply()
                            continue
                        }
                    }
                    else {
                        status.compareAndSet(UNDECIDED, FAILED)
                        return
                    }
                }

                if (!array[index2].compareAndSet(expected2, this)) {
                    val value = array[index2].value
                    if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                        if (value != this) {
                            value.apply()
                            continue
                        }
                    }
                    else {
                        status.compareAndSet(UNDECIDED, FAILED)
                        if (status.value == FAILED) {
                            rollback()
                        }
                        return
                    }
                }

                status.compareAndSet(UNDECIDED, SUCCESS)
                if (status.value == SUCCESS) {
                    array[index1].compareAndSet(this, update1)
                    array[index2].compareAndSet(this, update2)
                }
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