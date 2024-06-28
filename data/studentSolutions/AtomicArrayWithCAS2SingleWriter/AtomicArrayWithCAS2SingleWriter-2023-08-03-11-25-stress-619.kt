package day3

import day3.AtomicArrayWithCAS2SingleWriter.Status.*
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

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
        return when (val cell = array[index].value) {
            is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> cell.get(index) as E // WHY?
            else -> cell as E
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E, index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = CAS2Descriptor(
            index1 = index1,
            expected1 = expected1,
            update1 = update1,
            index2 = index2,
            expected2 = expected2,
            update2 = update2
        )
        descriptor.apply()
        return descriptor.status.value === SUCCESS
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

        fun apply() {
            while (true) {
                if (array[index1].compareAndSet(expected1, this)) {
                    if (array[index2].compareAndSet(expected2, this)) {
                        if (status.compareAndSet(UNDECIDED, SUCCESS)) {
                            if (array[index1].compareAndSet(this, update1)) {
                                if (array[index2].compareAndSet(this, update2)) {
                                    return
                                }
                            }
                        }
                    } else {
                        if (status.compareAndSet(UNDECIDED, FAILED)) {
                            array[index1].compareAndSet(this, expected1)
                            array[index2].compareAndSet(this, expected2)
                        } else {
                            return
                        }
                    }
                } else {
                    if (status.compareAndSet(UNDECIDED, FAILED)) {
                        array[index1].compareAndSet(this, expected1)
                        array[index2].compareAndSet(this, expected2)
                    } else {
                        return
                    }
                }
            }
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            // TODO: In this task, only one thread can call cas2(..),
            // TODO: so cas2(..) calls cannot be executed concurrently.
        }

//        fun installDescriptor() {
//            while (true) {
//                if (array[index1].compareAndSet(expected1, this)) {
//                    if (array[index2].compareAndSet(expected2, this)) {
//                        return
//                    }
//                } else {
//
//                }
//            }
//        }

        fun get(index: Int): E = when (status.value) {
            UNDECIDED, FAILED -> when (index) {
                index1 -> expected1
                index2 -> expected2
                else -> error("descriptor must contain $index")
            }

            SUCCESS -> when (index) {
                index1 -> update1
                index2 -> update2
                else -> error("descriptor must contain $index")
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}