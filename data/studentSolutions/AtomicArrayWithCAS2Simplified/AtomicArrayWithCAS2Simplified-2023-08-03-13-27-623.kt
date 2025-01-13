@file:Suppress("DuplicatedCode")

//package day3

import AtomicArrayWithCAS2Simplified.Status.*
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
        val e = array[index].value

        return if (e is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            if (e.status.value === SUCCESS) {
                if (index == e.index1) e.update1 as E
                else e.update2 as E
            } else {
                if (index == e.index1) e.expected1 as E
                else e.expected2 as E
            }
        } else {
            e as E
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
        return descriptor.status.value === SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        private val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            val status = if (array[index1].compareAndSet(expected1, this) && array[index2].compareAndSet(expected2, this))
                SUCCESS else FAILED
            if (this.status.compareAndSet(UNDECIDED, status)) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}