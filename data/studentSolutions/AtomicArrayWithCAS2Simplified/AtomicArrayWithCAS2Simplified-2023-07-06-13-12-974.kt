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

        while (true) {
            descriptor.apply()
            if (descriptor.status.value == SUCCESS) return true
            if (descriptor.status.value == FAILED) return false
        }
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
            if (array[index1].value == this || array[index1].compareAndSet(expected1, this)) {
                if (array[index2].value == this || array[index2].compareAndSet(expected2, this)) {
                    status.compareAndSet(UNDECIDED, SUCCESS)
                } else {
                    (array[index2].value as? AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor)?.let {
                        if (it != this) {
                            it.apply()
                            apply()
                            return
                        }
                    } ?: status.compareAndSet(UNDECIDED, FAILED)
                }
            } else {
                (array[index1].value as? AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor)?.let {
                    if (it != this) {
                        it.apply()
                        apply()
                        return
                    }
                } ?: status.compareAndSet(UNDECIDED, FAILED)
            }
            /*if (array[index1].value == this &&
                array[index2].value == this &&
                array[index1].value == this
            ) {
                status.compareAndSet(UNDECIDED, SUCCESS)
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }*/

            if (status.value == FAILED) {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
            } else if (status.value == SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}