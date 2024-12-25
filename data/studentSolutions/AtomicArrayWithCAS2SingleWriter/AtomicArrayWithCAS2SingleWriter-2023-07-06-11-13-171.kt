//package day3

import day3.AtomicArrayWithCAS2SingleWriter.Status.*
import kotlinx.atomicfu.*
import java.lang.IllegalStateException

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
        when (val e = array[index].value) {
            is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor -> {
                return if (e.status.value == UNDECIDED || e.status.value == FAILED) {
                    when(index) {
                        e.index1 -> e.expected1 as E
                        else -> e.expected2 as E
                    }
                } else {
                    when(index) {
                        e.index1 -> e.update1 as E
                        else -> e.update2 as E
                    }
                }
            }
            else -> return e as E
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: use CAS2Descriptor to fix it.
        // TODO: Note that only one thread can call CAS2!
        val ds1 = CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        if (array[index1].compareAndSet(expected1, ds1) && array[index2].compareAndSet(expected2, update2)) {
            array[index1].compareAndSet(ds1, update1)
        }
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
            // TODO: install the descriptor, update the status, update the cells.
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}