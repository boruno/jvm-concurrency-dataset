package day4

import day4.AtomicArrayWithDCSS.Status.*
import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class AtomicArrayWithDCSS<E>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E {
        val value = array[index].value

        val result = if (value is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
            when (value.status) {
                SUCCESS -> value.update1
                else -> value.expected1
            }
        } else {
            value
        }

        @Suppress("UNCHECKED_CAST")
        return result as E
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        // TODO: the cell can store a descriptor
        /*val value = array[index].value

        if (
            value is AtomicArrayWithDCSS<*>.DCSSDescriptor
            && get(index) == expected
        ) {
            return array[index].compareAndSet(value, update)
        }

        return array[index].compareAndSet(expected, update)*/
        return dcss(
            index, expected, update,
            index, expected
        )
    }

    fun dcss(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }

        val descriptor = DCSSDescriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2,
        )

        descriptor.applyOperation()

        return descriptor.status == SUCCESS
    }

    override fun toString(): String {
        val list = mutableListOf<Any?>()

        for (i in 0 until array.size) {
            list.add(array[i].value)
        }

        return "AtomicArrayWithDCSS($list)"
    }

    private interface Descriptor {
        val status: Status

        fun applyOperation()
    }

    private inner class DCSSDescriptor(
        val index1: Int, val expected1: E, val update1: E,
        val index2: Int, val expected2: E,
    ) : Descriptor {
        private val atomicStatus = atomic(UNDECIDED)

        override val status: Status
            get() = atomicStatus.value

        override fun applyOperation() {
            if (!array[index1].compareAndSet(expected1, this)) {
                atomicStatus.compareAndSet(UNDECIDED, FAILED)
            }

            if (
                array[index1].value == this
                && array[index2].value == expected2
            ) {
                atomicStatus.compareAndSet(UNDECIDED, SUCCESS)
            } else {
                atomicStatus.compareAndSet(UNDECIDED, FAILED)
            }

            when (status) {
                UNDECIDED -> error("Status should be SUCCESS or FAILED")

                SUCCESS -> array[index1].compareAndSet(this, update1)

                FAILED -> array[index1].compareAndSet(this, expected1)
            }
        }

        override fun toString(): String {
            return "DCSS(${status}, [$index1] $expected1 -> $update1 if [$index2] $expected2)"
        }
    }

    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}
