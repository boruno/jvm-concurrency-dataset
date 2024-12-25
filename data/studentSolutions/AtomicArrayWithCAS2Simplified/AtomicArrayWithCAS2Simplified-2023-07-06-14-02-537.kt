//package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.*


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
        val v = array[index].value
        return if (v is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            if (v.status.value == SUCCESS) {
                v.getUpdated(index) as E
            } else {
                v.getExpected(index) as E
            }
        } else {
            v as E
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = if (index1 < index2) CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        else CAS2Descriptor(index2, expected2, update2, index1, expected1, update1)
        descriptor.apply()
        return descriptor.status.value == SUCCESS
    }

    inner class CAS2Descriptor(
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E,
        private val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            val res = tryInstallDescriptor()
            if (res) {
                status.compareAndSet(UNDECIDED, SUCCESS)
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }
            updateValues()
        }

        private fun updateValues() {
            if (status.value == SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            }
            else {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
            }
        }

        private fun tryInstallDescriptor(): Boolean {
            return tryInstallDescriptor(index1, expected1) &&
                    tryInstallDescriptor(index2, expected2)
        }

        private fun tryInstallDescriptor(index: Int, expected: E): Boolean {
            while (true) {
                val curState = array[index].value
                when {
                    curState === this -> return true
                    curState is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> curState.apply()
                    curState === expected -> {
                        if (array[index].compareAndSet(expected, this)) {
                            return true
                        }
                    }
                    else -> return false
                }
            }
        }


        fun getExpected(idx: Int): E {
            return if (index1 == idx) expected1
            else if (index2 == idx) expected2
            else throw IllegalArgumentException("$idx")
        }

        fun getUpdated(idx: Int): E {
            return if (index1 == idx) update1
            else if (index2 == idx) update2
            else throw IllegalArgumentException("$idx")
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}