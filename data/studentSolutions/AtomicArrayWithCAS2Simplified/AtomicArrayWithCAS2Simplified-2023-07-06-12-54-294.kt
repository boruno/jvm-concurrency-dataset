//package day3

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
        var idx1 = index1
        var idx2 = index2
        var exp1 = expected1
        var exp2 = expected2
        var up1 = update1
        var up2 = update2
//        if (index1 > index2) {
//            idx1 = index2
//            idx2 = index1
//            exp1 = expected2
//            exp2 = expected1
//            up1 = update2
//            up2 = update1
//        }
        val descriptor = CAS2Descriptor(idx1, exp1, up1, idx2, exp2, up2)
        return descriptor.apply()
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
            return applyFromFirst()
            // TODO: install the descriptor, update the status, update the cells.
        }

        fun applyFromFirst(): Boolean {
            if (array[index1].compareAndSet(expected1, this)) {
                return applyFromSecond()
            } else {
                val value1 = array[index1].value
                if (value1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    if (value1 != this) {
                        value1.apply()
                    } else {
                        return applyFromSecond()
                    }
                    return applyFromFirst()
                } else {
                    return false
                }
            }
        }

        fun applyFromSecond(): Boolean {
            if (array[index2].compareAndSet(expected2, this)) {
                return success()
            } else {
                val value2 = array[index2].value
                if (value2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    if (value2 != this) {
                        value2.apply()
                    } else {
                        return success()
                    }
                    return applyFromSecond()
                } else {
                    status.compareAndSet(UNDECIDED, FAILED)
                    array[index1].compareAndSet(this, expected1)
                    return false
                }
            }
        }

        fun success() : Boolean{
            status.compareAndSet(UNDECIDED, SUCCESS)
            array[index1].compareAndSet(this, update1)
            array[index2].compareAndSet(this, update2)
            return true
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}