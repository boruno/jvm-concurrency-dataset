package day3

import day3.AtomicArrayWithDCSS.Status.*
import kotlinx.atomicfu.*

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
        val ref = array[index]
        val value = ref.value
        if (value is AtomicArrayWithDCSS<*>.DCSSDescriptor<*, *>) {
            val isSuccess = value.status.value == SUCCESS
            return if (isSuccess) {
                value.update1
            } else {
                value.expected1
            } as E
        }
        return value as E
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        // TODO: the cell can store a descriptor
        return array[index].compareAndSet(expected, update)
    }

    fun dcss(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        return DCSSDescriptor(index1, expected1, update1, index2, expected2).apply()
    }

    inner class DCSSDescriptor<V1, V2>(
        val index1: Int,
        val expected1: V1,
        val update1: V1,
        val index2: Int,
        val expected2: V2
    ) {
        val status = atomic(UNDECIDED)
        fun apply(): Boolean {
            if (!array[index1].compareAndSet(expected1, this)) {
                status.compareAndSet(UNDECIDED, FAILED)
                return false
            }
            if (array[index2].value === expected2) {
                status.compareAndSet(UNDECIDED, SUCCESS)
                array[index1].compareAndSet(this, update1)
                return true
            }
            status.compareAndSet(UNDECIDED, FAILED)
            return false
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}