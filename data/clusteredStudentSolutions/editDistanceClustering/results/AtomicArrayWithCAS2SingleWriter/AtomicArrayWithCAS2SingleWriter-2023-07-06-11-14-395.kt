//package day3

import day3.AtomicArrayWithCAS2SingleWriter.Status.*
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
        // TODO: the cell can store CAS2Descriptor
        val value = array[index].value

        if (value is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            return value.read(index) as E
        }

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
        return CAS2Descriptor(index1, expected1, update1,
            index2, expected2, update2).apply()
    }

    inner class CAS2Descriptor(
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E,
        private val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply(): Boolean {
            // TODO: install the descriptor, update the status, update the cells.

            if (!array[index1].compareAndSet(expected1, this)) return false

            return if (array[index2].compareAndSet(expected2, this)) {
                if (!this.status.compareAndSet(UNDECIDED, SUCCESS)) throw IllegalStateException("waaaa")
                if (!array[index1].compareAndSet(this, update1)) throw IllegalStateException("waaaa")
                if (!array[index2].compareAndSet(this, update2)) throw IllegalStateException("waaaa")
                true
            } else {
                if (!this.status.compareAndSet(UNDECIDED, FAILED)) throw IllegalStateException("waaaa")
                if (!array[index1].compareAndSet(this, expected1)) throw IllegalStateException("waaaa")
                false
            }
        }

        fun read(index: Int): E {
            val status =  status.value
            return if (status in listOf(UNDECIDED, FAILED)) {
                if (index == index1) expected1 else expected2
            } else {
                if (index == index2) update1 else update2
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}