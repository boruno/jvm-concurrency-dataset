package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.*
import kotlin.math.max
import kotlin.math.min


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
        val ref = array[index]
        val value = ref.value
        if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            val isSuccess = value.status.value == SUCCESS
            return if (isSuccess) {
                if (index == value.index1) value.update1 else value.update2
            } else {
                if (index == value.index1) value.expected1 else value.expected2
            } as E
        }
        return value as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        return descriptor.apply()
    }

    inner class CAS2Descriptor(
        index1_: Int,
        expected1_: E,
        update1_: E,
        index2_: Int,
        expected2_: E,
        update2_: E
    ) {
        val index1 = if (index1_ < index2_) index1_ else index2_
        val index2 = if (index1_ < index2_) index2_ else index1_
        val expected1 = if (index1_ < index2_) expected1_ else expected2_
        val expected2 = if (index1_ < index2_) expected2_ else expected1_
        val update1 = if (index1_ < index2_) update1_ else update2_
        val update2 = if (index1_ < index2_) update2_ else update1_

        val status = atomic(UNDECIDED)

        fun apply(): Boolean {
            while (true) {
                when (setDescriptor(index1, expected1)) {
                    true -> break
                    false -> return false
                    null -> continue
                }
            }
            return applyNext()
        }

        fun setDescriptor(index: Int, expected: E): Boolean? {
            val descriptor = this
            val ref = array[index]
            if (descriptor.status.value == SUCCESS) {
                return true
            }
            if (descriptor.status.value == FAILED) {
                return false
            }
            if (!ref.compareAndSet(expected, descriptor)) {
                val value = ref.value
                if (value === descriptor) {
                    return true
                }
                if (value === expected) {
                    return null
                }
                if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    if (value.applyNext()) {
                        return false
                    }
                    return null
                }
                if (!descriptor.status.compareAndSet(UNDECIDED, FAILED)) {
                    if (descriptor.status.value == SUCCESS) {
                        return true
                    }
                }
                return false
            }
            return true
        }

        fun applyNext(): Boolean {
            val descriptor = this
            val index1 = index1
            val ref1 = array[index1]
            while (true) {
                when (setDescriptor(index2, expected2)) {
                    true -> break
                    false -> {
                        ref1.compareAndSet(descriptor, expected1)
                        return false
                    }
                    null -> continue
                }
            }

            return final()
        }

        fun final(): Boolean {
            val descriptor = this
            val index1 = index1
            val index2 = index2
            val ref1 = array[index1]
            val ref2 = array[index2]
            if (descriptor.status.compareAndSet(UNDECIDED, SUCCESS) || descriptor.status.value == SUCCESS) {
                ref1.compareAndSet(descriptor, update1)
                ref2.compareAndSet(descriptor, update2)
                return true
            }
            ref1.compareAndSet(descriptor, expected1)
            ref2.compareAndSet(descriptor, expected2)
            return false
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}

/**
 * 0 - Dsc (A)
 * 1 - lock (A)
 */