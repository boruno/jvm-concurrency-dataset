package day3

import day3.AtomicArrayWithCAS2SingleWriter.Status.*
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

// This implementation never stores `null` values.
class AtomicArrayWithCAS2SingleWriter<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E {
        val value = array[index].value
        if (value is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            return value.read(index) as E
        }
        return value as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
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

        fun read(index: Int): E {
            val currentStatus = status.value
            if (currentStatus == SUCCESS) return if (index == index1) update1 else update2
            return if (index == index1) expected1 else expected2
        }

        fun apply() {
            val install1 = array[index1].compareAndSet(expected1, this)
            val install2 = array[index2].compareAndSet(expected2, this)
            if (install1 && install2) {
                status.compareAndSet(UNDECIDED, SUCCESS)
                array[index1].compareAndSet(this, update1)
                array[index1].compareAndSet(this, update2)
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
                array[index1].compareAndSet(this, expected1)
                array[index1].compareAndSet(this, expected2)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}