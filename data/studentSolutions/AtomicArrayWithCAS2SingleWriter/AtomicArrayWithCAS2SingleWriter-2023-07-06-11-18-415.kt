//package day3

import day3.AtomicArrayWithCAS2SingleWriter.Status.*
import kotlinx.atomicfu.*
import kotlin.math.exp

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
        // дескриптор
        val arrayCell = array[index].value
        if (arrayCell is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            when (arrayCell.status.value) {
                SUCCESS -> arrayCell.update(index)
                else -> arrayCell.expected(index)
            }
        }
        // не дескриптор
        return array[index].value as E
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

        val cas2Desc = CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        return cas2Desc.apply()
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

        fun apply(): Boolean {
            // TODO: install the descriptor, update the status, update the cells.
            // update the status
            if (array[index1].compareAndSet(expected1, this)) {
                if (array[index2].compareAndSet(expected2, this)) {
                    status.compareAndSet(UNDECIDED, SUCCESS)
                } else {
                    status.compareAndSet(UNDECIDED, FAILED)
                }
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }

            // update the cells
            return when (status.value) {
                SUCCESS -> {
                    array[index1].value = update1
                    array[index2].value = update2
                    true
                }

                else -> {
                    array[index1].value = expected1
                    array[index2].value = expected2
                    false
                }
            }
        }

        fun expected(index: Int): E {
            if (index == index1) {
                return expected1
            }
            return expected2
        }

        fun update(index: Int): E {
            if (index == index1) {
                return update1
            }
            return update2
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}