package day3

import day3.AtomicArrayWithDCSS.Status.*
import kotlinx.atomicfu.*

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
        val ref = array[index]
        val value = ref.value
        if (value is AtomicArrayWithDCSS<*>.DCSSDescriptor<*, *>) {
            val isSuccess = value.status.value == SUCCESS
            return if (isSuccess) {
                value.update1
            } else {
                value.expected1
            } as E
        }
        return value as E
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        val ref = array[index]
        while (true) {
            if (!ref.compareAndSet(expected, update)) {
                val value = ref.value
                if (value === expected) {
                    continue
                }
                if (value is AtomicArrayWithDCSS<*>.DCSSDescriptor<*, *>) {
                    value.applyNext()
                    continue
                }
                return false
            }
            return true
        }
    }

    fun dcss(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        return DCSSDescriptor(index1, expected1, update1, index2, expected2).apply()
    }

    inner class DCSSDescriptor<V1, V2>(
        val index1: Int,
        val expected1: V1,
        val update1: V1,
        val index2: Int,
        val expected2: V2
    ) {
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

        fun setDescriptor(index: Int, expected: V1): Boolean? {
            val descriptor = this
            val ref = array[index]
            if (!ref.compareAndSet(expected, descriptor)) {
                val value = ref.value
                if (value === descriptor) {
                    return true
                }
                if (value === expected) {
                    return null
                }
                if (value is AtomicArrayWithDCSS<*>.DCSSDescriptor<*, *>) {
                    value.applyNext()
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

        fun checkValue(index: Int, expected: V2): Boolean? {
            val descriptor = this
            val ref = array[index]
            val value = ref.value
            if (value !== expected) {
                if (value is AtomicArrayWithDCSS<*>.DCSSDescriptor<*, *>) {
                    value.applyNext()
                    return null
                }
                if (!descriptor.status.compareAndSet(UNDECIDED, FAILED)) {
                    if (descriptor.status.value == SUCCESS) {
                        return true
                    }
                }
                return false
            }
            descriptor.status.compareAndSet(UNDECIDED, SUCCESS)
            return true
        }

        fun applyNext(): Boolean {
            while (true) {
                when (checkValue(index2, expected2)) {
                    true -> break
                    false -> break
                    null -> continue
                }
            }

            return final()
        }

        fun final(): Boolean {
            val descriptor = this
            val index1 = index1
            val ref1 = array[index1]
            if (descriptor.status.value == SUCCESS) {
                ref1.compareAndSet(descriptor, update1)
                return true
            }
            ref1.compareAndSet(descriptor, expected1)
            return false
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}

/**
  2 - B (0)
  2 - B
 */