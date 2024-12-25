@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray


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

        return if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            when (value.status.get()!!) {
                UNDECIDED, FAILED -> value.getExpected(index) as E

                SUCCESS -> value.getUpdate(index) as E
            }
        } else {
            value as E
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = if (index1 < index2)
            CAS2Descriptor(
                index1 = index1, expected1 = expected1, update1 = update1,
                index2 = index2, expected2 = expected2, update2 = update2
            )
        else
            CAS2Descriptor(
                index1 = index2, expected1 = expected2, update1 = update2,
                index2 = index1, expected2 = expected1, update2 = update1
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

        fun getExpected(index: Int) = if (index == index1) expected1 else expected2
        fun getUpdate(index: Int) = if (index == index1) update1 else update2

        fun apply() {
            val isLogicallyUpdated = installDescriptors()
            updateStatus(isSuccess = isLogicallyUpdated)
            installPhysically()
        }


        private fun installDescriptors(): Boolean {
            if (!installDescriptor(index1)) return false
            return installDescriptor(index2)
        }

        fun installDescriptor(index: Int): Boolean {
            val value = array[index]
            if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                when (value.status.get()!!) {
                    UNDECIDED -> {
                        val otherIndex = if (value.index1 == index) index2 else index1

                        val isLogicallyUpdated = array.compareAndSet(otherIndex, value.getExpected(otherIndex), value)
                        value.updateStatus(isSuccess = isLogicallyUpdated)
                        value.installPhysically()

                        return array.compareAndSet(
                            index,
                            if (isLogicallyUpdated) value.getUpdate(otherIndex) else value.getExpected(otherIndex),
                            this
                        )
                    }

                    SUCCESS -> {
                        return array.compareAndSet(index, value.getUpdate(index), this)
                    }

                    FAILED -> {
                        return array.compareAndSet(index, value.getExpected(index), this)
                    }
                }
            }

            return array.compareAndSet(index, getExpected(index), this)
        }

        private fun updateStatus(isSuccess: Boolean): Boolean {
            return status.compareAndSet(UNDECIDED, if (isSuccess) SUCCESS else FAILED)
        }

        private fun installPhysically(): Boolean {
            return when (status.get()!!) {
                SUCCESS -> true

                FAILED -> array.compareAndSet(index1, this, expected1) &&
                        array.compareAndSet(index2, this, expected2)

                UNDECIDED -> array.compareAndSet(index1, this, update1) &&
                        array.compareAndSet(index2, this, update2)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}