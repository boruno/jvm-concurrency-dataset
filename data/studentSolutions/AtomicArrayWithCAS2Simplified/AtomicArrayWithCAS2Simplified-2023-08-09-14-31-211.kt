@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import AtomicArrayWithCAS2Simplified.Status.*
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray


// This implementation never stores `null` values.
class AtomicArrayWithCAS2Simplified<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    fun get(index: Int): E {
        // TODO: the cell can store CAS2Descriptor
        val state = array[index]
        if (state is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            if (state.status.get() != SUCCESS) {
                if (index == state.index1) {
                    //array.compareAndSet(index, state, state.expected1)
                    return state.expected1 as E
                }
                if (index == state.index2) {
                    //array.compareAndSet(index, state, state.expected2)
                    return state.expected2 as E
                }
            } else {
                if (index == state.index1) {
                    //array.compareAndSet(index, state, state.update1)
                    return state.update1 as E
                }
                if (index == state.index2) {
                    //array.compareAndSet(index, state, state.update2)
                    return state.update2 as E
                }
            }
        }

        return state as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }

        val descriptor = if (index2 < index1) CAS2Descriptor(
            index1 = index2, expected1 = expected2, update1 = update2,
            index2 = index1, expected2 = expected1, update2 = update1
        ) else CAS2Descriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2, update2 = update2
        )
        descriptor.apply()
        return descriptor.status.get() === SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = AtomicReference(UNDECIDED)

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            // Help other descriptor
//            val val1 = array[index1]
//            val val2 = array[index2]
//            if (val1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
//                val1.setValues()
//            } else if (val2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
//                val2.setValues()
//            }
            if(status.get() != UNDECIDED) {
                return
            }
            val installDesc = installDescriptor()
            val newStatus = if (installDesc) SUCCESS else FAILED
            if (status.compareAndSet(UNDECIDED, newStatus)) {
                setValues()
            }
        }

        private fun installDescriptor() : Boolean {
            //while (true) {
                if (array.compareAndSet(index1, expected1, this)) {
                    if (array.compareAndSet(index2, expected2, this)) {
                        return true
                    } else {
                        val descr = array[index2]
                        if (descr is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor && descr != this) {
                            descr.apply()
                            this.apply()
                        }
                        return false
                    }
                } else {
                    val descr = array[index1]
                    if (descr is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor && descr != this) {
                        descr.apply()
                        this.apply()
                    }
                    return false
                }
            //}
        }

        private fun setValues() {
            if (status.get() == SUCCESS) {
                if (array.compareAndSet(index1, this, update1)) {

                }
                if (array.compareAndSet(index2, this, update2)) {

                }

            } else if (status.get() == FAILED) {
                if (array.compareAndSet(index1, this, expected1)) {

                }
                if (array.compareAndSet(index2, this, expected2)) {

                }
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}