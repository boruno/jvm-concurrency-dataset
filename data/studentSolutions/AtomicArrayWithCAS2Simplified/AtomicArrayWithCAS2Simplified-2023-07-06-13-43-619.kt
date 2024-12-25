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
        return when (val value = array[index].value) {
            is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor -> {
                if (value.status.value == AtomicArrayWithCAS2SingleWriter.Status.SUCCESS) {
                    if (index == value.index1) return value.update1 as E
                    else return value.update2 as E
                } else {
                    if (index == value.index1) return value.expected1 as E
                    else return value.expected2 as E
                }
            }
            else -> {
                value as E
            }
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: use CAS2Descriptor to fix it.
        val descriptor = if (index1 >= index2) {
            CAS2Descriptor(index2, expected2, update2, index1, expected1, update1)
        } else {
            CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        }
        descriptor.apply()
        return descriptor.status.value == SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            if (status.value == UNDECIDED) {
                if (installDescriptor(true)) {
                    if (installDescriptor(false)) {
                        status.compareAndSet(UNDECIDED, SUCCESS)
                    } else {
                        status.compareAndSet(UNDECIDED, FAILED)
                    }
                } else {
                    status.compareAndSet(UNDECIDED, FAILED)
                }
            }

            if (status.value == FAILED) {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
            } else if (status.value == SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            }
        }

        private fun installDescriptor(first: Boolean): Boolean {
            val index = if (first) index1 else index2
            val expected = if (first) expected1 else expected2
            array[index].compareAndSet(expected, this)
            return when (val value = array[index].value) {
                this -> true
                is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
                    if (value != this) {
                        value.apply()
                        installDescriptor(first)
                    } else true
                }
                else -> false
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}