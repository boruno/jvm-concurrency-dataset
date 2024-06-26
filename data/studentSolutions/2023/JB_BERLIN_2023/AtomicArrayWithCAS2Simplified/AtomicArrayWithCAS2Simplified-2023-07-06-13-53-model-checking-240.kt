package day3

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
        val value = array[index].value
        if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            return value.read(index) as E
        }
        return value as E
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

    private inner class CAS2Descriptor(
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E,
        private val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun read(index: Int): E {
            check(index == index1 || index == index2)
            val currentStatus = status.value
            if (currentStatus == SUCCESS) return if (index == index1) update1 else update2
            return if (index == index1) expected1 else expected2
        }

        fun apply() {
            if (status.value == UNDECIDED) {
                val install1 = installOrHelp(true)
                val install2 = installOrHelp(false)

                if (install1 && install2) {
                    status.compareAndSet(UNDECIDED, SUCCESS)
                }
            }

            val success = status.value == SUCCESS
            val update1 = if (success) update1 else expected1
            val update2 = if (success) update2 else expected2
            array[index1].compareAndSet(this, update1)
            array[index2].compareAndSet(this, update2)
        }

        private fun installOrHelp(first: Boolean): Boolean {
            while (true) {
                if (status.value != UNDECIDED) return false
                val index = if (first) index1 else index2
                val expected = if (first) expected1 else expected2
                val current = array[index].value
                if (current === this) {
                    return true
                } else if (current is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    current.apply()
                } else if (current !== expected) {
                    status.compareAndSet(UNDECIDED, FAILED)
                    return false
                } else if (array[index].compareAndSet(current, this)) {
                    return true
                }
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}