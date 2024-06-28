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
        // дескриптор
        val arrayCell = array[index].value
        if (arrayCell is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            when (arrayCell.status.value) {
                AtomicArrayWithCAS2SingleWriter.Status.SUCCESS -> return arrayCell.update(index) as E
                else -> return arrayCell.expected(index) as E
            }
        }
        // не дескриптор
        return arrayCell as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: use CAS2Descriptor to fix it.
        if (index1 < index2) {
            return CAS2Descriptor(index1, expected1, update1, index2, expected2, update2).apply()
        } else {
            return CAS2Descriptor(index2, expected2, update2, index1, expected1, update1).apply()
        }
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
            // update the status and cells

            // в первом стоит какой то другой дексриптор
//            val arrayIndex1 = array[index1]
//            val arrayIndex2 = array[index2]
            val desc1 = array[index1].value
            if (desc1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor && desc1 != this) {
                (desc1 as AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor).apply()
            }

            if (desc1 == this || array[index1].compareAndSet(expected1, this)) {

                val desc2 = array[index2].value

                if (desc2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor && desc2 != this) {
                    // первый дескриптор мы поставили, а во втором уже кто-то поставил свое. надо им помочь
                    (desc2 as AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor).apply()
                }

                if (desc2 == this || array[index2].compareAndSet(expected2, this)) {
                    status.compareAndSet(UNDECIDED, SUCCESS)
                    array[index1].compareAndSet(this, update1)
                    array[index2].compareAndSet(this, update2)
                    return true
                } else {
                    status.compareAndSet(UNDECIDED, FAILED)
                    array[index1].compareAndSet(this, expected1)
                    array[index2].compareAndSet(this, expected2)
                }
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
                array[index1].compareAndSet(this, expected1)
            }


            return false
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}