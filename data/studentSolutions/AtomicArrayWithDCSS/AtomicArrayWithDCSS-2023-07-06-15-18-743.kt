//package day3

import AtomicArrayWithDCSS.Status.*
import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class AtomicArrayWithDCSS<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E? {
        return when (val value = array[index].value) {
            is AtomicArrayWithDCSS<*>.CAS2Descriptor -> {
                if (value.status.value == SUCCESS) {
                    return value.update1 as E
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

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        // TODO: the cell can store a descriptor
        if (array[index].compareAndSet(expected, update)) return true
        val value = array[index].value as? AtomicArrayWithDCSS<*>.CAS2Descriptor
        return when (value) {
            null -> false
            else -> {
                value.apply()
                cas(index, expected, update)
            }
        }
    }

    fun dcss(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = CAS2Descriptor(index1, expected1, update1, index2, expected2)
        descriptor.apply()
        return descriptor.status.value == SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            if (status.value == UNDECIDED) {
                if (installDescriptor() && array[index2].value == expected2 && array[index1].value == this) {
                    status.compareAndSet(UNDECIDED, SUCCESS)
                } else {
                    status.compareAndSet(UNDECIDED, FAILED)
                }
            }

            if (status.value == FAILED) {
                array[index1].compareAndSet(this, expected1)
            } else if (status.value == SUCCESS) {
                array[index1].compareAndSet(this, update1)
            }
        }

        private fun installDescriptor(): Boolean {
            return when (val value = array[index1].value) {
                this -> true
                expected1 -> {
                    array[index1].compareAndSet(expected1, this)
                    installDescriptor()
                }

                is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
                    value.apply()
                    installDescriptor()
                }

                else -> false
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}