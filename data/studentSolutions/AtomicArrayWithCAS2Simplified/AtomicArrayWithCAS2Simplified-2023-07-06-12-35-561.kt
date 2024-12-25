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
        val value = array[index].value
        if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            val leftNotRight = value.index1 == index
            return when (value.status.value) {
                SUCCESS -> if (leftNotRight) value.update1 as E else value.update2 as E
                else -> if (leftNotRight) value.expected1 as E else value.expected2 as E
            }
        } else {
            return value as E
        }
    }

    fun compareAndSetAndHelp(index : Int, expected: E, value: Any) : Boolean {
        if (array[index].compareAndSet(expected, value)) {
            return true
        }
        val descriptor =
            array[index].value as? AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor ?: return false

        when(descriptor.status.value) {
            SUCCESS -> array[index].compareAndSet(descriptor, descriptor.updateAtIndex(index))
            FAILED -> array[index].compareAndSet(descriptor, descriptor.expectedAtIndex(index))
            UNDECIDED -> return false
        }

        return array[index].compareAndSet(expected, value)
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }

        val descriptor = CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)

        if (!compareAndSetAndHelp(index1, expected1, descriptor)) {
            return false
        }
        if (!compareAndSetAndHelp(index2, expected2, descriptor)) {
            descriptor.status.compareAndSet(UNDECIDED, FAILED)
            array[index1].compareAndSet(descriptor, expected1)
        }

        descriptor.apply()

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
        val status = atomic(UNDECIDED)

        fun apply() {
            if (array[index1].compareAndSet(this, update1) &&
                array[index2].compareAndSet(this, update2)) {

                status.compareAndSet(UNDECIDED, SUCCESS)
            }
        }

        fun expectedAtIndex(index : Int) : E = when(index) {
            index1 -> expected1
            index2 -> expected2
            else -> throw IllegalStateException()
        }

        fun updateAtIndex(index : Int) : E = when(index) {
            index1 -> update1
            index2 -> update2
            else -> throw IllegalStateException("I am for $index1, $index2, not for $index")
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}