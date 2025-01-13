//package day4

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
        while (true) {
            val value = array[index].value!!
            if (value is Descriptor) {
                value.applyOperation()
            } else {
                @Suppress("UNCHECKED_CAST")
                return value as E?
            }
        }
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        // TODO: the cell can store a descriptor
        return array[index].compareAndSet(expected, update)
    }

    fun dcss(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }

        val descriptor = DCSSDescriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2,
        )

        descriptor.applyOperation()

        return descriptor.status.value == SUCCESS
    }

    private interface Descriptor {
        fun applyOperation()
    }

    private inner class DCSSDescriptor(
        val index1: Int, val expected1: E?, val update1: E?,
        val index2: Int, val expected2: E?,
    ) : Descriptor {
        val status = atomic(UNDECIDED)

        override fun applyOperation() {
            if (!array[index1].compareAndSet(expected1, this)) {
                status.compareAndSet(UNDECIDED, FAILED)
            }

            if (
                array[index1].value == this
                && array[index2].value === expected2
            ) {
                status.compareAndSet(UNDECIDED, SUCCESS)
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }

            when (status.value) {
               UNDECIDED -> error("Status should be SUCCESS or FAILED")

                SUCCESS -> array[index1].compareAndSet(this, update1)

                FAILED -> array[index1].compareAndSet(this, expected1)
            }
        }
    }

    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}
