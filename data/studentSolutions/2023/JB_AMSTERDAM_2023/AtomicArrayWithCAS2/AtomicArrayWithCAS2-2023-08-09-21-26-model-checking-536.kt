@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

package day3

import day3.AtomicArrayWithCAS2.Status.*
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray

// This implementation never stores `null` values.
class AtomicArrayWithCAS2<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    fun get(index: Int): E {
        return when (val currentValue = array[index]) {
            is Descriptor -> currentValue.get(index) as E
            is DcssDescriptor -> currentValue.expected as E
            else -> currentValue as E
        }
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        while (true) {
            val value = array[index]
            when {
                value == expected -> {
                    if (array.compareAndSet(index, expected, update)) {
                        return true
                    }
                }

                value is Descriptor -> value.apply()
                value != expected -> return false
            }
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E,
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = if (index1 < index2) {
            CAS2Descriptor(
                index1 = index1, expected1 = expected1, update1 = update1,
                index2 = index2, expected2 = expected2, update2 = update2,
            )
        } else {
            CAS2Descriptor(
                index1 = index2, expected1 = expected2, update1 = update2,
                index2 = index1, expected2 = expected1, update2 = update1,
            )
        }
        descriptor.apply()
        return descriptor.status.get() === SUCCESS
    }

    inner class CAS2Descriptor(
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E,
        private val update2: E,
    ) {
        val status = AtomicReference(UNDECIDED)

        fun get(index: Int): E {
            return when (status.get()) {
                UNDECIDED, FAILED -> if (index == index1) expected1 else expected2
                SUCCESS -> if (index == index1) update1 else update2
            }
        }

        fun apply() {
            if (tryInstallDescriptor(index1, expected1)) {
                tryInstallDescriptor(index2, expected2)
            }

            setDescriptorStatus()
            updateValues()
        }

        private fun tryInstallDescriptor(index: Int, expected: E): Boolean {
            if (status.get() != UNDECIDED) {
                return false
            }

            while (true) {
                when (val value = array[index]) {
                    expected -> {
                        if (!DCSSDescriptor(index, expected, this, status).apply()) {
                            return true
                        }
                    }
                    this -> return true
                    is Descriptor -> value.apply()
                    is DcssDescriptor -> value.apply()
                    else -> return false
                }
            }
        }

        private fun setDescriptorStatus() {
            status.compareAndSet(UNDECIDED, if (array[index1] == this && array[index2] == this) SUCCESS else FAILED)
        }

        private fun updateValues() {
            if (status.get() == SUCCESS) {
                array.compareAndSet(index1, this, update1)
                array.compareAndSet(index2, this, update2)
            } else {
                array.compareAndSet(index1, this, expected1)
                array.compareAndSet(index2, this, expected2)
            }
        }
    }

    inner class DCSSDescriptor(
        val index: Int,
        val expected: E,
        val update: Descriptor,
        val statusToCheck: AtomicReference<Status>,
    ) {
        private val status = AtomicReference(UNDECIDED)

        fun apply(): Boolean {
            tryInstallDcssDescriptor()

            if (array[index] == this) {
                status.compareAndSet(UNDECIDED, if (statusToCheck.get() == UNDECIDED) SUCCESS else FAILED)
            }

            return if (status.get() == SUCCESS) {
                array.compareAndSet(index, this, update)
                true
            } else {
                array.compareAndSet(index, this, expected)
                false
            }
        }

        private fun tryInstallDcssDescriptor() {
            if (status.get() != UNDECIDED) {
                return
            }

            while (true) {
                when (val value = array[index]) {
                    expected -> array.compareAndSet(index, expected, this)
                    this -> return
                    is Descriptor -> value.apply()
                    is DcssDescriptor -> value.apply()
                    else -> return
                }
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}

private typealias Descriptor = AtomicArrayWithCAS2<*>.CAS2Descriptor
private typealias DcssDescriptor = AtomicArrayWithCAS2<*>.DCSSDescriptor
