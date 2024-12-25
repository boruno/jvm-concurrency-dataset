@file:Suppress("DuplicatedCode")

//package day3

import kotlinx.atomicfu.*
import day3.AtomicArrayWithCAS2.Status.*

// This implementation never stores `null` values.
class AtomicArrayWithCAS2<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E = when (val value = array[index].value) {
        is AtomicArrayWithCAS2<*>.CAS2Descriptor -> value.apply()[index]
        else -> value
    } as E

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        // TODO: the cell can store a descriptor
        return array[index].compareAndSet(expected, update)
    }

    fun cas2(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?, update2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = CAS2Descriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2, update2 = update2
        )
        descriptor.apply()
        return descriptor.status.value === SUCCESS
    }

    private fun dcss(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = DCSSDescriptor(index1, expected1, update1, index2, expected2)
        descriptor.apply()
        return descriptor.status.value == SUCCESS
    }

    private inner class CAS2Descriptor(
        index1: Int,
        expected1: E?,
        update1: E?,
        index2: Int,
        expected2: E?,
        update2: E?,
    ) {
        val status = atomic(UNDECIDED)

        private val index1: Int
        private val expected1: E?
        private val update1: E?
        private val index2: Int
        private val expected2: E?
        private val update2: E?

        init {
            if (index1 < index2) {
                this.index1 = index1
                this.index2 = index2
                this.expected1 = expected1
                this.update1 = update1
                this.expected2 = expected2
                this.update2 = update2
            } else {
                this.index1 = index2
                this.index2 = index1
                this.expected1 = expected2
                this.update1 = update2
                this.expected2 = expected1
                this.update2 = update1
            }
        }

        private fun values(): NewValue<E?> {
            return when (status.value) {
                SUCCESS -> NewValue(index1, update1, index2, update2)
                FAILED -> NewValue(index1, expected1, index2, expected2)
                UNDECIDED -> error("Cannot get value from undecided state")
            }
        }

        fun apply(): NewValue<E?> {
            if (status.value != UNDECIDED) {
                return updateCells()
            }
            if (installDescriptor(index1, expected1) && installDescriptor(index2, expected2)) {
                status.compareAndSet(UNDECIDED, SUCCESS)
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }
            return updateCells()
        }

        fun installDescriptor(index: Int, expected: E?): Boolean {
            while (true) {
                val value = array[index].value
                if (value is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                    if (value == this) {
                        return true
                    } else {
                        value.apply()
                    }
                } else if (value != expected) {
                    return false
                }
                if (array[index].compareAndSet(expected, this)) {
                    return true
                }
            }
        }

        private fun updateCells(): NewValue<E?> {
            val values = values()
            array[index1].compareAndSet(this, values.newValue1)
            array[index2].compareAndSet(this, values.newValue2)
            return values
        }
    }

    private inner class DCSSDescriptor(
        private val index1: Int,
        private val expected1: E?,
        private val update1: E?,
        private val index2: Int,
        private val expected2: E?,
    ) {

        val status = atomic(UNDECIDED)

        fun help(): E? {
            if (status.value == UNDECIDED) {
                setStatus()
            }
            return updateCell()
        }

        fun value(): E? {
            return when (status.value) {
                SUCCESS -> update1
                FAILED -> expected1
                UNDECIDED -> error("Cannot get value from undecided state")
            }
        }

        fun apply(): E? {
            val value1 = array[index1].value
            if (value1 is AtomicArrayWithCAS2<*>.DCSSDescriptor && value1 != this) {
                value1.help()
            }
            if (array[index1].compareAndSet(expected1, this)) {
                setStatus()
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }
            return updateCell()
        }

        fun setStatus() {
            var value = array[index2].value
            if (value is AtomicArrayWithCAS2<*>.DCSSDescriptor) {
                value = if (value.expected1 == value.update1) {
                    value.expected1
                } else {
                    value.value()
                }
            }
            if (value == expected2) {
                status.compareAndSet(UNDECIDED, SUCCESS)
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }
        }

        fun updateCell(): E? {
            val newValue = value()
            array[index1].compareAndSet(this, newValue)
            return newValue
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }

    private data class NewValue<E>(val index1: Int, val newValue1: E, val index2: Int, val newValue2: E) {
        operator fun get(index: Int): E = when (index) {
            index1 -> newValue1
            index2 -> newValue2
            else -> throw ArrayIndexOutOfBoundsException(index)
        }
    }
}