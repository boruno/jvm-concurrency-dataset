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

        private fun installDescriptor(): Boolean {
            var value1 = array[index1].value
            while (true) {
                if (value1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    // Another thread has already installed a descriptor
                    if (value1 == this) {
                        // It's the same descriptor. Now look at the other cell.
                        if (array[index2].value == this) {
                            // The other cell also contains this descriptor.
                            // Nothing to do anymore.
                            return true
                        }
                        return value1.install(index2)
                    }
                    value1.apply()
                }
                if (install(index1)) {
                    if (install(index2)) return true
                    val value2 = array[index2].value
                    if (value2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor || value2 == expected2) continue
                    return false
                }
                value1 = array[index1].value
                if (value1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor || value1 == expected1) continue
                return false
            }
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

    private data class NewValue<E : Any>(val index1: Int, val newValue1: E, val index2: Int, val newValue2: E) {
        operator fun get(index: Int): E = when (index) {
            index1 -> newValue1
            index2 -> newValue2
            else -> throw ArrayIndexOutOfBoundsException(index)
        }
    }
}