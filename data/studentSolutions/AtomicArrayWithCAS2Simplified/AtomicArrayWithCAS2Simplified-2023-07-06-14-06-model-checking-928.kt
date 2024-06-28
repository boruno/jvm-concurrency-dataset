package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
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

    fun get(index: Int): E {
        // TODO: the cell can store CAS2Descriptor
        val value = array[index].value
        if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            val status = value.status.value
            return if (status == SUCCESS) {
                if (index == value.index1) {
                    value.update1 as E
                } else {
                    value.update2 as E
                }
            } else {
                if (index == value.index1) {
                    value.expected1 as E
                } else {
                    value.expected2 as E
                }
            }
        }
        return value as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: use CAS2Descriptor to fix it.
        val descriptor = if (index1 < index2) {
            CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        } else {
            CAS2Descriptor(index2, expected2, update2, index1, expected1, update1)
        }
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
            if (status.value === UNDECIDED) {
                val success = tryInstallDescriptor()
                if (success){
                    status.compareAndSet(UNDECIDED, SUCCESS)
                } else {
                    status.compareAndSet(UNDECIDED, FAILED)
                }
                updateValues()
            }
        }

        private fun tryInstallDescriptor(): Boolean {
            if (!tryInstallDescriptor(index1, expected1)) return false
            if (!tryInstallDescriptor(index2, expected2)) return false
            return true
        }

        private fun tryInstallDescriptor(index: Int, expected: E): Boolean {
            while (true) {
                val curState = array[index].value
                when {
                    curState === this -> return true
                    curState is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
                        curState.apply()
                    }
                    curState === expected -> {
                        if (array[index].compareAndSet(expected, this)) {
                            return true
                        }
                    }
                    else -> {
                        return false
                    }
                }
            }
        }

        private fun updateValues() {
            if (status.value === SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            } else {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
            }
        }

//        fun apply(startFromSecond: Boolean): Boolean {
//            if (status.value == SUCCESS) {
//                return finishSuccess()
//            }
//            if (status.value == FAILED) {
//                return finishFailed()
//            }
//            if (!startFromSecond) {
//                when (val value1 = array[index1].value) {
//                    this -> return apply(true)
//                    is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
//                        value1.apply(false) // help
//                        return apply(false) // us
//                    }
//
//                    expected1 -> {
//                        if (array[index1].compareAndSet(expected1, this)) {
//                            return apply(true) // continue for second
//                        } else {
//                            return apply(false) // try again
//                        }
//                    }
//
//                    else -> {
//                        status.compareAndSet(UNDECIDED, FAILED)
//                        return false
//                    }
//                }
//            } else {
//                val value2 = array[index2].value
//
//
//                if (value2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
//                    if (value2 == this) {
//                        status.compareAndSet(UNDECIDED, SUCCESS)
//                        return finishSuccess()
//                    } else {
//                        value2.apply(false)
//                        return apply(true)
//                    }
//                } else {
//                    if (array[index2].compareAndSet(expected2, this)) {
//                        return apply(true)
//                    } else {
//                        status.compareAndSet(UNDECIDED, FAILED)
//                        return apply(true)
//                    }
//                }
//            }
//            // TODO: install the descriptor, update the status, update the cells.
//        }
//        fun finishSuccess() : Boolean{
//            array[index1].compareAndSet(this, update1)
//            array[index2].compareAndSet(this, update2)
//            return true
//        }
//
//        fun finishFailed() : Boolean {
//            array[index1].compareAndSet(this, expected1)
//            return false
//        }
    }


    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}