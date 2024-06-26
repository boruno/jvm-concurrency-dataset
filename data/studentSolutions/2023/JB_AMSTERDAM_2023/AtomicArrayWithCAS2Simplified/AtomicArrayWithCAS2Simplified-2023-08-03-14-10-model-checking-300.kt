package day3

import kotlinx.atomicfu.*
import kotlin.math.exp


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

        if (value !is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            return value as E
        }

        val descriptor: AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor = value
        when (descriptor.status.value) {
            Status.UNDECIDED, Status.FAILED -> {
                if (descriptor.index1 == index)
                    return descriptor.expected1 as E

                if (descriptor.index2 == index)
                    return descriptor.expected1 as E

                throw IllegalStateException()
            }

            Status.SUCCESS -> {
                if (descriptor.index1 == index)
                    return descriptor.update1 as E

                if (descriptor.index2 == index)
                    return descriptor.update2 as E

                throw IllegalStateException()
            }

        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }


        val descriptor =
            if (index1 <= index2)
                CAS2Descriptor(
                    index1 = index1, expected1 = expected1, update1 = update1,
                    index2 = index2, expected2 = expected2, update2 = update2
                )
            else {
                CAS2Descriptor(
                    index1 = index2, expected1 = expected2, update1 = update2,
                    index2 = index1, expected2 = expected1, update2 = update1
                )
            }
        descriptor.apply()
        return descriptor.status.value === Status.SUCCESS
    }

    inner class CAS2Descriptor(
        internal val index1: Int,
        internal val expected1: E,
        internal val update1: E,
        internal val index2: Int,
        internal val expected2: E,
        internal val update2: E
    ) {
        internal val status = atomic(Status.UNDECIDED)


        fun apply() {
            if (installAtCell(index1, expected1) && installAtCell(index2, expected2)) {
                updateStatus(Status.SUCCESS)
            } else {
                updateStatus(Status.FAILED)
            }

            check(status.value != Status.UNDECIDED)
            updateValuesInCells()
        }

        private fun installAtCell(index: Int, expected: E): Boolean {
            while (status.value == Status.UNDECIDED) {
                val currentValue = array[index].value
                if (currentValue == expected) {
                    if (array[index].compareAndSet(expected, this)) {
                        return true
                    } else {
                        continue
                    }
                }

                if (currentValue == this) {
                    return true
                }

                if (currentValue is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    currentValue.apply()
                    continue
                }

                return false
            }

            return false
        }

        private fun updateStatus(newStatus: Status) {
            status.compareAndSet(Status.UNDECIDED, newStatus)
        }

        private fun updateValuesInCells() {
            when (status.value) {
                Status.SUCCESS -> {
                    array[index1].compareAndSet(this, this.update1)
                    array[index2].compareAndSet(this, this.update2)
                }

                Status.FAILED -> {
                    array[index1].compareAndSet(this, this.expected1)
                    array[index2].compareAndSet(this, this.expected2)
                }

                Status.UNDECIDED -> Unit
            }
        }
    }

    internal enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}
