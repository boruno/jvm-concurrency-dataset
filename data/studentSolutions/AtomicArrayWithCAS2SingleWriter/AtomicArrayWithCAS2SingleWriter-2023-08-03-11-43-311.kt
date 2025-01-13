//package day3

import AtomicArrayWithCAS2SingleWriter.Status.*
import AtomicArrayWithCAS2
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

    fun get(index: Int): E? {
        while(true) {
            val cur = array[index].value!!
            if (cur is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
                cur.apply()
                continue
            }
            return cur as E?
        }
        // TODO: the cell can store CAS2Descriptor
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
         val index2: Int,
         val expected2: E,
         val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
                if (status.value == UNDECIDED) {
                    status.compareAndSet(UNDECIDED, SUCCESS)
                }

            if (status.value == UNDECIDED) {
                val val1 = array[index1].value
                val val2 = array[index2].value
                if (val1 != this || val2 != this) {
                    if (status.compareAndSet(UNDECIDED, FAILED)) {
                        array[index1].compareAndSet(this, expected1)
                        array[index2].compareAndSet(this, expected2)
                    }
                    return
                }
                if (status.compareAndSet(UNDECIDED, SUCCESS)) {
                    array[index1].compareAndSet(this, update1)
                    array[index2].compareAndSet(this, update2)
                }
                return
            }

                if (status.value == SUCCESS) {
                    if (array[index1].compareAndSet(this, update1) &&
                        array[index2].compareAndSet(this, update2)){
                        status.compareAndSet(SUCCESS, SUCCESS)
                        return
                    } else {
                        status.compareAndSet(SUCCESS, FAILED)
                        return
                    }
                }

//            if (array[index1].compareAndSet(expected1, update1) && array[index2].compareAndSet(expected2, update2)) {
//                status.compareAndSet(UNDECIDED, SUCCESS)
//            } else {
//                status.compareAndSet(UNDECIDED, FAILED)
//            }
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            // TODO: In this task, only one thread can call cas2(..),
            // TODO: so cas2(..) calls cannot be executed concurrently.
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}
