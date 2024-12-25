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
        while (true) {
            val value = array[index].value
            if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                value.apply()
            } else {
                return value as E
            }
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }

        val descriptor = if (index1 < index2) {
            CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        } else {
            CAS2Descriptor(index2, expected2, update2, index1, expected1, update1)
        }

        descriptor.apply()

        return descriptor.status.value == SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            if (status.value == UNDECIDED) {
                val success = tryInstallDescriptor()
                if (success) {
                    status.compareAndSet(UNDECIDED, SUCCESS)
                } else {
                    status.compareAndSet(UNDECIDED, FAILED)
                }
            }
            updateValues()
        }

        private fun updateValues() {
            if (status.value == SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            } else {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
            }
        }

        private fun tryInstallDescriptor(): Boolean {
            return tryInstallDescriptor(index1, expected1) &&
                    tryInstallDescriptor(index2, expected2)
        }

        private fun tryInstallDescriptor(idx: Int, expected: E): Boolean {
            while (true) {
                val current = array[index1].value
                when {
                    current == expected -> {
                        if (array[index1].compareAndSet(expected, this)) {
                            return true
                        } else {
                            continue
                        }
                    }

                    current == this -> return true
                    current is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor -> {
                        current.apply()
                        continue
                    }

                    else -> return false
                }
            }
        }

        fun valueAtIndex(index: Int): E = when (status.value) {
            SUCCESS -> updateAtIndex(index)
            else -> expectedAtIndex(index)
        }

        fun expectedAtIndex(index: Int): E = when (index) {
            index1 -> expected1
            index2 -> expected2
            else -> throw IllegalStateException()
        }

        fun updateAtIndex(index: Int): E = when (index) {
            index1 -> update1
            index2 -> update2
            else -> throw IllegalStateException("I am for $index1, $index2, not for $index")
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}