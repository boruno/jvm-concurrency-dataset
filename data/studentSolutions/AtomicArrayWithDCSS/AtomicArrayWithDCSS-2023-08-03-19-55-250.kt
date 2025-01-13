//package day3

import kotlinx.atomicfu.*
import AtomicArrayWithDCSS.Status.*

// This implementation never stores `null` values.
class AtomicArrayWithDCSS<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E? {
        // TODO: the cell can store a descriptor
        return array[index].value as E?
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
        val descriptor = DCSSDescriptor(index1, expected1, update1, index2, expected2)
        descriptor.apply()
        return descriptor.status.value == SUCCESS
    }

    private inner class DCSSDescriptor(
        private val index1: Int,
        private val expected1: E?,
        private val update1: E?,
        private val index2: Int,
        private val expected2: E?,
    ) {

        val status = atomic(UNDECIDED)

        fun apply() {
            val value1 = array[index1].value
            if (value1 is AtomicArrayWithDCSS<*>.DCSSDescriptor) {

            }
            if (array[index1].compareAndSet(expected1, this)) {
                if (array[index2].value == expected2) {
                    status.compareAndSet(UNDECIDED, SUCCESS)
                } else {
                    status.compareAndSet(UNDECIDED, FAILED)
                }
            }
            if (status.value == SUCCESS) {
                array[index1].compareAndSet(this, update1)
            } else if (status.value == FAILED) {
                array[index1].compareAndSet(this, expected2)
            }
        }
    }

    private enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}