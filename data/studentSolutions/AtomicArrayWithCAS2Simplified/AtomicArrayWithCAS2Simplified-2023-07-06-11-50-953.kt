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
        val curVal = array[index].value
        val result = if (curVal is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            when {
                curVal.status.value != SUCCESS && index == curVal.index1 -> curVal.expected1
                curVal.status.value != SUCCESS && index == curVal.index2 -> curVal.expected2
                curVal.status.value == SUCCESS && index == curVal.index1 -> curVal.update1
                curVal.status.value == SUCCESS && index == curVal.index2 -> curVal.update2
                else -> error("unreachable")
            }
        } else {
            curVal
        }
        @Suppress("UNCHECKED_CAST")
        return result as E
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
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            while (!array[index1].compareAndSet(expected1, this)) {
                val value = array[index1].value
                if (value == this) {
                    break
                }
                if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    value.apply()
                    continue
                }
                status.compareAndSet(UNDECIDED, FAILED)
                return
            }
            while (!array[index2].compareAndSet(expected2, this)) {
                val value = array[index2].value
                if (value == this) {
                    break
                }
                if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    value.apply()
                    continue
                }
                status.compareAndSet(UNDECIDED, FAILED)
                array[index1].compareAndSet(this, expected1)
                return
            }
            status.compareAndSet(UNDECIDED, SUCCESS)
            array[index1].compareAndSet(this, update1)
            array[index2].compareAndSet(this, update2)
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}