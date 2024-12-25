//package day3

import kotlinx.atomicfu.*
import day3.AtomicArrayWithDCSS.Status.*
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

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E? = when (val value = array[index].value) {
        is AtomicArrayWithDCSS<*>.DCSSDescriptor -> {
            value.help()
        }
        else -> value
    } as E?

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        while (true) {
            if (array[index].compareAndSet(expected, update)) return true
            val value = array[index].value
            if (value !is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
                return array[index].compareAndSet(expected, update)
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
        private val expected1: E?,
        private val update1: E?,
        private val index2: Int,
        private val expected2: E?,
    ) {

        val status = atomic(UNDECIDED)

        fun help(): E? {
            if (status.value == UNDECIDED) {
                setStatus()
            }
            return updateCell()
        }

        fun value(): E? {
            return when (status.value) {
                SUCCESS -> update1
                FAILED -> expected1
                UNDECIDED -> error("Cannot get value from undecided state")
            }
        }

        fun apply(): E? {
            val value1 = array[index1].value
            if (value1 is AtomicArrayWithDCSS<*>.DCSSDescriptor && value1 != this) {
                value1.help()
            }
            if (array[index1].compareAndSet(expected1, this)) {
                setStatus()
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }
            return updateCell()
        }

        fun setStatus() {
            var value2 = array[index2].value
            if (value2 is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
                value2 = value2.help()
            }
            if (value2 == expected2) {
                status.compareAndSet(UNDECIDED, SUCCESS)
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }
        }

        fun updateCell(): E? {
            val newValue = value()
            array[index1].compareAndSet(this, newValue)
            return newValue
        }
    }

    private enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}