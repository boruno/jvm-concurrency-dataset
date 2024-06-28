package day3

import kotlinx.atomicfu.*
import day3.AtomicArrayWithDCSS.Status.*

// This implementation never stores `null` values.
class AtomicArrayWithDCSS<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E? = when (val value = array[index].value) {
        is AtomicArrayWithDCSS<*>.DCSSDescriptor ->
            if (value.status.value == SUCCESS) value.update1 else value.expected1
        else -> value
    } as E?

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        while (true) {
            if (array[index].compareAndSet(expected, update)) return true
            val value = array[index].value
            if (value !is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
                return false
            }
            value.help()
        }
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
        val expected1: E?,
        val update1: E?,
        private val index2: Int,
        private val expected2: E?,
    ) {

        val status = atomic(UNDECIDED)

        fun help() {
            when (status.value) {
                SUCCESS -> array[index1].compareAndSet(this, update1)
                FAILED -> array[index1].compareAndSet(this, expected1)
                UNDECIDED -> apply()
            }
        }

        fun apply() {
            val value1 = array[index1].value
            if (value1 is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
                value1.help()
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