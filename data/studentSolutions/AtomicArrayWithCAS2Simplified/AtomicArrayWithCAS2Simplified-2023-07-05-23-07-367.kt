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

    @Synchronized
    fun get(index: Int): E {
        // TODO: the cell can store CAS2Descriptor
        return array[index].value as E
    }

    @Synchronized
    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: use CAS2Descriptor to fix it.
        if (array[index1].value != expected1 || array[index2].value != expected2) return false
        array[index1].value = update1
        array[index2].value = update2
        return true
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
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}