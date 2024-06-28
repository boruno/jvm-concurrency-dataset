@file:Suppress("DuplicatedCode")

package day3

import day3.AtomicArrayWithCAS2.Status.*
import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class AtomicArrayWithCAS2<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E {
        return when (val value = array[index].value) {
            is AtomicArrayWithCAS2<*>.DCSSDescriptor -> {
                if (value.status.value == SUCCESS) {
                    return value.update1 as E
                } else {
                    return value.expected1 as E
                }
            }
            is AtomicArrayWithCAS2<*>.CAS2Descriptor -> {
                if (value.status.value == SUCCESS) {
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

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        // TODO: the cell can store a descriptor
        val value = array[index].value
        return when (value) {
            is AtomicArrayWithCAS2<*>.DCSSDescriptor -> {
                value.apply()
                cas(index, expected, update)
            }
            is AtomicArrayWithCAS2<*>.CAS2Descriptor -> {
                value.apply()
                cas(index, expected, update)
            }
            expected -> {
                if (array[index].compareAndSet(expected, update)) true
                else cas(index, expected, update)
            }
            else -> false
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: Store a CAS2 descriptor in array[index1].
        val descriptor = if (index1 >= index2) {
            CAS2Descriptor(index2, expected2, update2, index1, expected1, update1)
        } else {
            CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        }
        descriptor.apply()
        return descriptor.status.value == SUCCESS
    }

    fun dcss(
        index1: Int, expected1: E, update1: Any,
        descriptor: CAS2Descriptor
    ): Boolean {
        val descriptor = DCSSDescriptor(index1, expected1, update1, descriptor)
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
                if (installDescriptors()) {
                    status.compareAndSet(UNDECIDED, SUCCESS)
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

        private fun installDescriptors(): Boolean {
            if (!installDescriptor(true)) return false
            if (!installDescriptor(false)) return false
            return true
        }

        private fun installDescriptor(first: Boolean): Boolean {
            val index = if (first) index1 else index2
            val expected = if (first) expected1 else expected2
            return when (val value = array[index].value) {
                this -> true
                expected -> {
                    dcss(index, expected1, this, this)
                    array[index].compareAndSet(expected, this)
                    installDescriptor(first)
                }

                is AtomicArrayWithCAS2<*>.CAS2Descriptor -> {
                    value.apply()
                    installDescriptor(first)
                }

                else -> false
            }
        }
    }

    inner class DCSSDescriptor(
        val index1: Int,
        val expected1: E,
        val update1: Any,
        val descriptor: CAS2Descriptor
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            if (status.value == UNDECIDED) {
                if (installDescriptor() && descriptor.status.value == UNDECIDED && array[index1].value == this) {
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
                    if (array[index1].compareAndSet(expected1, this)) return true else installDescriptor()
                }

                is AtomicArrayWithCAS2<*>.CAS2Descriptor -> {
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