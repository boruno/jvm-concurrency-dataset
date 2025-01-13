@file:Suppress("DuplicatedCode", "UNCHECKED_CAST", "WHEN_ENUM_CAN_BE_NULL_IN_JAVA")

//package day3

import AtomicArrayWithCAS2Simplified.Status.*
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray


// This implementation never stores `null` values.
class AtomicArrayWithCAS2Simplified<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    fun get(index: Int): E {
        return when (val currentValue = array[index]) {
            is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> currentValue.get(index) as E
            else -> currentValue as E
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
        index1: Int,
        expected1: E,
        update1: E,
        index2: Int,
        expected2: E,
        update2: E
    ) {
        val status = AtomicReference(UNDECIDED)

        private val index1 = if (index1 < index2) index1 else index2
        private val expected1: E = if (index1 < index2) expected1 else expected2
        private val update1: E = if (index1 < index2) update1 else update2
        private val index2: Int = if (index1 < index2) index2 else index1
        private val expected2: E = if (index1 < index2) expected2 else expected1
        private val update2: E = if (index1 < index2) update2 else update1

        fun get(index: Int): E {
            return when (status.get()) {
                UNDECIDED, FAILED -> if (index == index1) expected1 else expected2
                SUCCESS -> if (index == index1) update1 else update2
            }
        }

        fun apply() {
            val descriptorInstalled = tryInstallDescriptors()

            setDescriptorStatus(descriptorInstalled)

            updateValues()
        }

        private fun tryInstallDescriptors(): Boolean {
            if (!tryInstallDescriptor(index1, expected1)) {
                return false
            }

            return tryInstallDescriptor(index2, expected2)
        }

        private fun tryInstallDescriptor(index: Int, expected: E): Boolean {
            while (true) {
                when (val value = array[index]) {
                    this -> return true
                    is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
                        if (value.status.get() == UNDECIDED) {
                            // help another
                            value.apply()
                        }
                    }

                    else -> {
                        if (value != expected) {
                            return false
                        }
                        return array.compareAndSet(index, value, this)
                    }
                }
            }
        }

        private fun setDescriptorStatus(descriptorInstalled: Boolean) {
            if (descriptorInstalled) {
                status.compareAndSet(UNDECIDED, SUCCESS)
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }
        }

        private fun updateValues() {
            if (status.get() == SUCCESS) {
                array.compareAndSet(index1, this, update1)
                array.compareAndSet(index2, this, update2)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}
