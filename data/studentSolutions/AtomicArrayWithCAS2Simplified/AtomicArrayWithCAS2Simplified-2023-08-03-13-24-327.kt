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

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        val value = array[index].value
        if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            return when (value.status.value) {
                UNDECIDED, FAILED -> value.getExpected(index)
                SUCCESS -> value.getUpdated(index)
            } as E
        }
        return value as E
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

        fun apply() {
            if (installDescriptor()) {
                status.compareAndSet(UNDECIDED, SUCCESS)
                updateCells()
            }
            status.compareAndSet(UNDECIDED, FAILED)
        }

        private fun installDescriptor(): Boolean {
            val value1 = array[index1].value
            if (value1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                // Another thread has already installed a descriptor
                if (value1 == this) {
                    return array[index2].compareAndSet(expected2, this)
                }
                value1.apply()
            }
            if (array[index1].compareAndSet(expected1, this)) {
                if (array[index2].compareAndSet(expected2, this)) {
                    return true
                }
            }
            return false
        }

        private fun updateCells() {
            when (status.value) {
                SUCCESS -> {
                    array[index1].compareAndSet(this, update1)
                    array[index2].compareAndSet(this, update2)
                }
                FAILED -> {
                    array[index1].compareAndSet(this, expected1)
                    array[index2].compareAndSet(this, expected2)
                }
                UNDECIDED -> return
            }
        }

        fun getExpected(index: Int) = when (index) {
            index1 -> expected1
            index2 -> expected2
            else -> invalidIndex(index)
        }

        fun getUpdated(index: Int) = when (index) {
            index1 -> update1
            index2 -> update2
            else -> invalidIndex(index)
        }

        private fun invalidIndex(index: Int): Nothing {
            error("Invalid index: $index, expected $index1 or $index2")
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}