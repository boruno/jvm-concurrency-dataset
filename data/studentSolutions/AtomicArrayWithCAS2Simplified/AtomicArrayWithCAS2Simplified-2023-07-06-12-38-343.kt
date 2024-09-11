package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.*
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
        val curVal = array[index].value
        val descriptor = curVal as? CAS2Descriptor<E> ?: return curVal as E
        return when (descriptor.status.value) {
            UNDECIDED -> if (descriptor.index1 == index) descriptor.expected1 else descriptor.expected2
            FAILED -> if (descriptor.index1 == index) descriptor.expected1 else descriptor.expected2
            SUCCESS -> if (descriptor.index1 == index) descriptor.update1 else descriptor.update2
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        // TODO: this implementation is not linearizable,
        // TODO: use CAS2Descriptor to fix it.
        require(index1 != index2) { "The indices should be different" }
        val descriptor = CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        return descriptor()
    }
    operator fun CAS2Descriptor<E>.invoke(): Boolean{
        val from1 = min(index1, index2) == index1
        val firstExpected = if (from1) expected1 else expected2
        val secondExpected = if (from1) expected2 else expected1
        val firstUpdate = if (from1) update1 else update2
        val secondUpdate = if (from1) update2 else update1
        val firstIndex = if (from1) index1 else index2
        val secondIndex = if (from1) index2 else index1
        if (array[secondIndex].compareAndSet(secondExpected, this)) {
            status.compareAndSet(UNDECIDED, SUCCESS)
            array[firstIndex].compareAndSet(this, firstUpdate)
            array[secondIndex].compareAndSet(this, secondUpdate)
            return true
        }
        val nextDescriptor = array[secondIndex].value as? CAS2Descriptor<E> ?: run {
            status.compareAndSet(UNDECIDED, FAILED)
            array[firstIndex].value = firstExpected
            return false
        }
        return nextDescriptor()
    }

    class CAS2Descriptor<E>(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            // TODO: install the descriptor, update the status, update the cells.
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}