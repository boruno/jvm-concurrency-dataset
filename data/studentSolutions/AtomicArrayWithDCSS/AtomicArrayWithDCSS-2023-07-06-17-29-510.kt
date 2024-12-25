//package day3

import day3.AtomicArrayWithDCSS.Status.*
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.math.exp

// This implementation never stores `null` values.
class AtomicArrayWithDCSS<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E {
        // TODO: the cell can store a descriptor
        return array[index].value as E
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        // TODO: the cell can store a descriptor
        return array[index].compareAndSet(expected, update)
    }

    fun dcss(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }

        val descriptor = DCSSDescriptor(index1, expected1, update1, index2, expected2)

        descriptor.apply()

        return descriptor.status.value === SUCCESS
    }

    private inner class DCSSDescriptor(
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            if (!array[index1].compareAndSet(expected1, this)) {
                status.compareAndSet(UNDECIDED, FAILED)
                return
            }
            val equal = array[index2].value === expected2
            val res = if (equal) SUCCESS else FAILED
            status.compareAndSet(UNDECIDED, res)
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}