//package day3

import AtomicArrayWithDCSS.Status.*
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

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
        val v = array[index].value
        return if (v is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
            v.getValue() as E
        }
        else {
            v as E
        }
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        while (true) {
            when (val v = array[index].value) {
                is AtomicArrayWithDCSS<*>.DCSSDescriptor -> v.apply()
                else -> return array[index].compareAndSet(expected, update)
            }
        }
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
            if (status.value === UNDECIDED) {
                if (areValuesEqual()) {
                    status.compareAndSet(UNDECIDED, SUCCESS)
                } else {
                    status.compareAndSet(UNDECIDED, FAILED)
                }
            }
            updateValue()
        }

        private fun updateValue() {
            if (status.value === SUCCESS) {
                array[index1].compareAndSet(this, update1)
            }
            else {
                array[index1].compareAndSet(this, expected1)
            }
        }

        fun areValuesEqual(): Boolean {
            return setDescriptor() && secondValueEqual()
        }

        fun setDescriptor(): Boolean {
            while (true) {
                when (val v1 = array[index1].value) {
                    this -> return true
                    is AtomicArrayWithDCSS<*>.DCSSDescriptor -> v1.apply()
                    expected1 -> {
                        if (array[index1].compareAndSet(expected1, this)) {
                            return true
                        }
                    }
                    else -> return false
                }
            }
        }

        private fun secondValueEqual(): Boolean {
            while (true) {
                when (val v2 = array[index2].value) {
                    is AtomicArrayWithDCSS<*>.DCSSDescriptor -> v2.apply()
                    expected2 -> return true
                    else -> return false
                }
            }
        }

        fun getValue(): E {
            return if (status.value === SUCCESS) update1
            else expected1
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}