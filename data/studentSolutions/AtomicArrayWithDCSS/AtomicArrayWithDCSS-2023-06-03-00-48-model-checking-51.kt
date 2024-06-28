package day4

import kotlinx.atomicfu.*
import day4.AtomicArrayWithDCSS.Status.*
// This implementation never stores `null` values.
class AtomicArrayWithDCSS<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E? {
        // TODO: the cell can store a descriptor
        while(true) {
            val cur = array[index].value!!
            if (cur is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
                cur.applyOperation()
                continue
            }
            //if (cur !is Int) throw Exception("${123}")
            return cur as E
        }
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        var counter = 0
        while(true) {
            counter += 1
            val cur = get(index)
            if (cur != expected) {
                return false
            }
            if (array[index].compareAndSet(expected, update)) {
                return true
            }
            if (counter > 10000) {
                throw Exception("BUSTED")
            }
        }
    }

    fun dcss(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO This implementation is not linearizable!
        // TODO Store a DCSS descriptor in array[index1].
        while(true) {
            val cur1 = get(index1)
            val cur2 = get(index2)
            if (cur1 != expected1 || cur2 != expected2) {
                return false
            }
            val descriptor = DCSSDescriptor(index1, expected1, index2, update1, expected2)
            if (array[index1].compareAndSet(expected1, this)) {
                descriptor.applyOperation()
                if (descriptor.status.value == SUCCESS) {
                    return true
                }
            }
        }
    }
    private inner class DCSSDescriptor(
        val index1: Int, val valueBefore: E,
        val index2: Int, val valueAfter: E, val expected1: E
    ) {
        val status = atomic(UNDECIDED)

        // TODO: Other threads can call this function
        // TODO: to help completing the operation.
        fun applyOperation() {
            if (status.value == UNDECIDED) {
                val val1 = array[index1].value
                val val2 = array[index2].value
                if (val1 != this || val2 != expected1) {
                    if (status.compareAndSet(UNDECIDED, FAILED)) {
                        array[index1].compareAndSet(this, valueBefore)
                    }
                    return
                }
                if (status.compareAndSet(UNDECIDED, SUCCESS)) {
                    array[index1].compareAndSet(this, valueAfter)
                }
            }
            if (status.value == SUCCESS) {
                array[index1].compareAndSet(this, valueAfter)
                return
            }
            if (status.value == FAILED) {
                array[index1].compareAndSet(this, valueBefore)
            }
        }
    }

    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}