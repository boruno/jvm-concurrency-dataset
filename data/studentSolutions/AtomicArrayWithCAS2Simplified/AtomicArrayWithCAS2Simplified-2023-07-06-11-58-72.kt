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
        val ref = array[index]
        val value = ref.value
        if (value is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            if (index == value.index1) return if (value.status.value == AtomicArrayWithCAS2SingleWriter.Status.SUCCESS) value.update1 as E else value.expected1 as E
            if (index == value.index2) return if (value.status.value == AtomicArrayWithCAS2SingleWriter.Status.SUCCESS) value.update2 as E else value.expected2 as E
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
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E,
        private val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply(): Boolean {
            val descriptor = this
            val index1 = index1
            val index2 = index2
            val ref1 = array[index1]
            val ref2 = array[index2]
            while (true) {
                if (!ref1.compareAndSet(expected1, descriptor)) {
                    val value1 = ref1.value
                    if (value1 !== descriptor) {
                        if (value1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                            value1.apply()
                            continue
                        } else if (value1 === expected1) {
                            continue
                        } else {
                            descriptor.status.value = FAILED
                            return false
                        }
                    }
                }
                break
            }

            while (true) {
                if (!ref2.compareAndSet(expected2, descriptor)) {
                    val value2 = ref2.value
                    if (value2 !== descriptor) {
                        if (value2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                            value2.apply()
                            continue
                        } else if (value2 === expected2) {
                            continue
                        } else {
                            descriptor.status.value = FAILED
                            ref1.compareAndSet(descriptor, expected1)
                            return false
                        }
                    }
                }
                break
            }

            descriptor.status.value = SUCCESS
            ref1.compareAndSet(descriptor, update1)
            ref2.compareAndSet(descriptor, update2)
            return true
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}