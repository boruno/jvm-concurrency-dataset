//package day3

import day3.AtomicArrayWithCAS2SingleWriter.Status.*
import kotlinx.atomicfu.*
import java.lang.Exception

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
        // TODO: the cell can store CAS2Descriptor
        val value = array[index].value
        if (value is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            if (value.status.value == SUCCESS) {
                if(index == value.index1) {
                    return value.update1 as E
                } else if(index == value.index2) {
                    return value.update2 as E
                }
                // return update
            } else {
                if(index == value.index1) {
                    return value.expected1 as E
                } else if(index == value.index2) {
                    return value.expected2 as E
                }
                // return expected
            }
        } else {
            return value as E
        }
//        return array[index].value as E

        throw Exception("asd")
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: use CAS2Descriptor to fix it.
        // TODO: Note that only one thread can call CAS2!

//        if (array[index1].value != expected1 || array[index2].value != expected2) return false
//        array[index1].value = update1
//        array[index2].value = update2
//        return true
        val descriptor = CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        if(array[index1].compareAndSet(expected1, descriptor)) {
            if(array[index2].compareAndSet(expected2, descriptor)) {
                descriptor.status.compareAndSet(UNDECIDED, SUCCESS)
                return true
            } else {
                descriptor.status.compareAndSet(UNDECIDED, FAILED)
                return false
            }
        } else {
            descriptor.status.compareAndSet(UNDECIDED, FAILED)
            return false
        }
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
            // TODO: install the descriptor, update the status, update the cells.
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}