@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import AtomicArrayWithCAS2Simplified.Status.*
import java.util.concurrent.atomic.*


// This implementation never stores `null` values.
class AtomicArrayWithCAS2Simplified<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    fun get(index: Int): E {
        val value = array[index]
        if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            return value.getValue(index) as E
        } else {
            return value as E
        }
    }

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
        return descriptor.status.get() === SUCCESS
    }

    inner class CAS2Descriptor(
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E,
        private val update2: E
    ) {
        val status = AtomicReference(UNDECIDED)
        val first =  if (index1 > index2) index2 else index1
        val second = if (index1 > index2) index1 else index2

        fun apply() {
            if (installDescriptor(first) && installDescriptor(second)) {
                status.compareAndSet(UNDECIDED, SUCCESS)
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }
            finish()
        }

        private fun installDescriptor(idx: Int) : Boolean {
            val expectedValue = getExpectedValue(idx)
            while (true) {
                if (status.get() != UNDECIDED) {
                    return false
                }
                val curValue = array[idx]
                if (curValue == this) {
                    return true
                }
                if (curValue is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    if (curValue == this) {
                        return true
                    } else {
                        curValue.apply()
                    }
                } else {
                    if (curValue == expectedValue) {
                        return array.compareAndSet(idx, expectedValue, this)
                    } else {
                        return false
                    }
                }
            }
        }

        private fun finish(){
            if (status.get() == SUCCESS){
                array.compareAndSet(first, this, getUpdatedValue(first))
                array.compareAndSet(second, this, getUpdatedValue(second))
            } else if (status.get() == FAILED) {
                array.compareAndSet(first, this, getExpectedValue(first))
                array.compareAndSet(second, this, getExpectedValue(second))
            }
        }

        fun getExpectedValue(i: Int) : E {
            if (i == index1) {
                return expected1
            } else {
                return expected2
            }
        }
        fun getUpdatedValue(i: Int) : E {
            if (i == index1) {
                return update1
            } else {
                return update2
            }
        }
        fun getValue(i: Int) : E {
            return if (status.get() != SUCCESS) {
                getExpectedValue(i)
            } else {
                getUpdatedValue(i)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}