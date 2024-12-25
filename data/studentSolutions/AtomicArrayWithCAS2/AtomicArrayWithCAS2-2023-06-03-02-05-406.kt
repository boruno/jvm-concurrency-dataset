@file:Suppress("DuplicatedCode")

//package day4

import day4.AtomicArrayWithCAS2.Status.*
import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class AtomicArrayWithCAS2<E : Any>(size: Int, initialValue: E) {
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
            if (cur is AtomicArrayWithCAS2<*>.DCSSDescriptor) {
                cur.applyOperation()
                continue
            }
            if (cur is AtomicArrayWithCAS2<*>.IncrementDescriptor) {
                cur.applyOperation()
                continue
            }
            return cur as E?
        }
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        while(true) {
            val cur = get(index)
            if (cur != expected) {
                return false
            }
            if (array[index].compareAndSet(expected, update)) {
                return true
            }
        }
    }

    fun cas2(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?, update2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: Store a CAS2 descriptor in array[index1].
        while(true) {
            val cur1 = get(index1)
            val cur2 = get(index2)
            if (cur1 != expected1 || cur2 != expected2) {
                return false
            }
            val descriptor = IncrementDescriptor(index1, expected1, index2, expected2, update1, update2)
            if (array[index1].compareAndSet(expected1, descriptor)) {
                descriptor.applyOperation()
                if (descriptor.status.value == SUCCESS) {
                    return true
                }
            }
        }
    }
    private fun dcss(
        index1: Int, expected1: E?, update1: Any,
        index2: IncrementDescriptor, expected2: Status
    ): Boolean {
        // TODO This implementation is not linearizable!
        // TODO Store a DCSS descriptor in array[index1].
        while(true) {
            val cur1 = get(index1)
            val cur2 = index2.status.value
            if (cur1 != expected1 || cur2 != expected2) {
                return false
            }
            val descriptor = DCSSDescriptor(index1, expected1, index2, update1, expected2)
            if (array[index1].compareAndSet(expected1, descriptor)) {
                descriptor.applyOperation()
                if (descriptor.status.value == SUCCESS) {
                    return true
                }
            }
        }
    }
    private inner class DCSSDescriptor(
        val index1: Int, val valueBefore: E?,
        val index2: IncrementDescriptor, val valueAfter: Any, val expected1: Status
    ) {
        val status = atomic(UNDECIDED)

        // TODO: Other threads can call this function
        // TODO: to help completing the operation.
        fun applyOperation() {
            if (status.value == UNDECIDED) {
                val val1 = array[index1].value
                val val2 = index2.status.value
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
    private inner class IncrementDescriptor(
        val index1: Int, val valueBeforeIncrement1: E?,
        val index2: Int, val valueBeforeIncrement2: E?,
        val valueAfter1: E?, val valueAfter2: E?
    ) {
        val status = atomic(UNDECIDED)

        // TODO: Other threads can call this function
        // TODO: to help completing the operation.
        fun applyOperation() {
            if (status.value == UNDECIDED) {
                val val1 = array[index1].value
                val val2 = array[index2].value
                val test2 = dcss(index2, valueBeforeIncrement2, this, this, UNDECIDED)
                if (val1 != this || val2 != this && !test2) {
                    if (status.compareAndSet(UNDECIDED, FAILED)) {
                        array[index1].compareAndSet(this, valueBeforeIncrement1)
                        array[index2].compareAndSet(this, valueBeforeIncrement2)
                    }
                    return
                }
                if (status.compareAndSet(UNDECIDED, SUCCESS)) {
                    array[index1].compareAndSet(this, valueAfter1)
                    array[index2].compareAndSet(this, valueAfter2)
                }
                return
            }
            if (status.value == SUCCESS) {
                array[index1].compareAndSet(this, valueAfter1)
                array[index2].compareAndSet(this, valueAfter2)
                return
            }
            if (status.value == FAILED) {
                array[index1].compareAndSet(this, valueBeforeIncrement1)
                array[index2].compareAndSet(this, valueBeforeIncrement2)
            }
        }
    }

    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}