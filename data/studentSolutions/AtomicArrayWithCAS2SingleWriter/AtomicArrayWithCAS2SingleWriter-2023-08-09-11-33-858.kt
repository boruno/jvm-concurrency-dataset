@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import AtomicArrayWithCAS2SingleWriter.Status.*
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray

// This implementation never stores `null` values.
class AtomicArrayWithCAS2SingleWriter<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    fun get(index: Int): E {
        return when (val currentValue = array[index]) {
            is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor -> {
                val status = currentValue.status.get()
                @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
                when (status) {
                    UNDECIDED, FAILED -> if (index == currentValue.index1) currentValue.expected1 as E else currentValue.expected2 as E
                    SUCCESS -> if (index == currentValue.index1) currentValue.update1 as E else currentValue.update2 as E
                }

            }

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
        val index1: Int, val expected1: E, val update1: E,
        val index2: Int, val expected2: E, val update2: E,
    ) {
        val status = AtomicReference(UNDECIDED)

        fun apply() {
            val currentValue1 = array[index1]
            when {
                currentValue1 is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor -> {
                    status.set(FAILED)
                    return
                }

                (currentValue1 != expected1) -> {
                    status.set(FAILED)
                    return
                }

                else -> {
                    if (!array.compareAndSet(index1, currentValue1, this)) {
                        status.set(FAILED)
                        return
                    }
                }
            }

            val currentValue2 = array[index2]
            when {
                currentValue2 is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor -> {
                    status.set(FAILED)
                    array[index1] = currentValue1
                    return
                }

                (currentValue2 != expected2) -> {
                    status.set(FAILED)
                    array[index1] = currentValue1
                    return
                }

                else -> {
                    if (!array.compareAndSet(index2, currentValue2, this)) {
                        status.set(FAILED)
                        return
                    }
                }
            }

            array[index1] = update1
            array[index2] = update2
            status.set(SUCCESS)
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}