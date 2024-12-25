//package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.*
import java.lang.Exception


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
        // TODO: the cell can store CAS2Descriptor
//        return array[index].value as E
        val value = array[index].value
        if (value is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            if (value.status.value == AtomicArrayWithCAS2SingleWriter.Status.SUCCESS) {
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
//        if (array[index1].value != expected1 || array[index2].value != expected2) return false
//        array[index1].value = update1
//        array[index2].value = update2
//        return true
        val descriptor = if (index1 < index2) {
            CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        } else {
            CAS2Descriptor(index2, expected2, update2, index1, expected1, update1)
        }
//        if(array[index1].compareAndSet(expected1, descriptor)) {
//            if(array[index2].compareAndSet(expected2, descriptor)) {
//                descriptor.status.compareAndSet(UNDECIDED, SUCCESS)
//                // apply
//                array[index1].compareAndSet(descriptor, update1)
//                array[index2].compareAndSet(descriptor, update2)
//
//                return true
//            } else {
//                descriptor.status.compareAndSet(UNDECIDED, FAILED)
//                array[index1].compareAndSet(descriptor, expected1)
////                array[index2].compareAndSet(descriptor, expected2)
//                return false
//            }
//        } else {
//            descriptor.status.compareAndSet(UNDECIDED, FAILED)
//            return false
//        }
        return descriptor.apply()
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

        fun apply():Boolean {
            // TODO: install the descriptor, update the status, update the cells.
            array[index1].value?.checkWorkFinished() ?: return true
            if (array[index1].compareAndSet(expected1, this)) {
                array[index2].value?.checkWorkFinished() ?: return true
                if(array[index2].compareAndSet(expected2, this)) {
                    status.compareAndSet(
                        UNDECIDED,
                        SUCCESS
                    )
                    // apply
                    array[index1].compareAndSet(this, update1)
                    array[index2].compareAndSet(this, update2)

                    return true
                } else {
                    status.compareAndSet(
                        UNDECIDED,
                        FAILED
                    )

                    array[index1].compareAndSet(this, expected1)
//                array[index2].compareAndSet(descriptor, expected2)
                    return false
                }
            } else {
                status.compareAndSet(
                    UNDECIDED,
                    FAILED
                )
                return false
            }
        }
        private fun Any.checkWorkFinished(): Boolean? {
            if(this is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                if(this == this@CAS2Descriptor) return null
                this.apply()
                return true
            } else {
                return false
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}