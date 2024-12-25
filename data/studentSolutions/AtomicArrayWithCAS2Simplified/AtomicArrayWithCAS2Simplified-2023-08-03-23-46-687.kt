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

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E = when (val value = array[index].value) {
        is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> value.apply()[index]
        else -> value
    } as E

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

    private inner class CAS2Descriptor(
        index1: Int,
        expected1: E,
        update1: E,
        index2: Int,
        expected2: E,
        update2: E
    ) {
        val status = atomic(UNDECIDED)

        private val index1: Int
        private val expected1: E
        private val update1: E
        private val index2: Int
        private val expected2: E
        private val update2: E

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

        private fun values(): NewValue<E> {
            return when (status.value) {
                SUCCESS -> NewValue(index1, update1, index2, update2)
                FAILED -> NewValue(index1, expected1, index2, expected2)
                UNDECIDED -> error("Cannot get value from undecided state")
            }
        }

        fun apply(): NewValue<E> {
            if (installDescriptor()) {
                status.compareAndSet(UNDECIDED, SUCCESS)
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }
            return updateCells()
        }

        fun installDescriptor(index: Int, expected: E): Boolean {
            while (true) {
                val value = array[index].value
                if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    // Another thread has already installed a descriptor
                    if (value == this) {
                        return true
                    } else {
                        value.apply()
                    }
                } else if (value != expected) {
                    return false
                }
                if (install(index)) {
                    return true
                }
            }
        }

        private fun installDescriptor(): Boolean {
            return installDescriptor(index1, expected1) && installDescriptor(index2, expected2)
        }

        private fun install(index: Int): Boolean {
            val expected = if (index == index1) expected1 else expected2
            return array[index].compareAndSet(expected, this)
        }

        private fun updateCells(): NewValue<E> {
            val values = values()
            array[index1].compareAndSet(this, values.newValue1)
            array[index2].compareAndSet(this, values.newValue2)
            return values
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }

    private data class NewValue<E : Any>(val index1: Int, val newValue1: E, val index2: Int, val newValue2: E) {
        operator fun get(index: Int): E = when (index) {
            index1 -> newValue1
            index2 -> newValue2
            else -> throw ArrayIndexOutOfBoundsException(index)
        }
    }
}