//package day3

import kotlinx.atomicfu.*

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
        if (value !is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor)
            return value as E

        val status = value.status.value
        if (status == Status.UNDECIDED || status == Status.FAILED)
            return if (value.index1 == index) value.expected1 as E else value.expected2 as E

        value.apply()

        return if (value.index1 == index) value.update1 as E else value.update2 as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: use CAS2Descriptor to fix it.
        // TODO: Note that only one thread can call CAS2!

        if (array[index1].value != expected1 || array[index2].value != expected2) return false

        val caS2Descriptor = CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        array[index1].value = caS2Descriptor
        array[index2].value = caS2Descriptor

        caS2Descriptor.status.value = Status.SUCCESS

        //caS2Descriptor.apply()

        return true
    }

    inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = atomic(Status.UNDECIDED)

        fun apply() {
            if (status.value == Status.SUCCESS) {
                array[index1].compareAndSet(expected1, update1)
                array[index2].compareAndSet(expected2, update2)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}