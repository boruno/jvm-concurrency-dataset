//package day3

import AtomicArrayWithDCSS.Status.*
import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class AtomicArrayWithDCSS<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E? {
        // TODO: the cell can store a descriptor
        val value = array[index].value
        return when{
            value is DCSSDescriptor<*> -> when((value as DCSSDescriptor<E>).status.value){
                UNDECIDED, FAIL ->value.expected1
                SUCCESS ->value.update1
            }
            value is CASDescriptor<*>->when((value as CASDescriptor<E>).status.value){
                UNDECIDED, FAIL -> value.expected1
                SUCCESS -> value.update1
            }
            else -> value as E?
        }
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        // TODO: the cell can store a descriptor
        val desc = CASDescriptor(index, expected, update)
        while (true) {
            val curVal = array[index].value
            when (curVal) {
                is DCSSDescriptor<*> -> curVal.apply()
                is CASDescriptor<*> -> curVal.apply()
                else -> {
                    if (!array[index].compareAndSet(expected, desc)){
                        return false
                    }
                    desc.apply()
                    return desc.status.value == SUCCESS
                }
            }
        }
    }

    fun dcss(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO This implementation is not linearizable!
        // TODO Store a DCSS descriptor in array[index1].
        val desc = DCSSDescriptor(index1, expected1, update1, index2, expected2)
        desc.installAndApply()
        return desc.status.value == SUCCESS
    }

    private fun <E> CASDescriptor<E>.apply() {
        status.compareAndSet(UNDECIDED, SUCCESS)
        array[index1].compareAndSet(this, update1)
    }
    private fun <E> DCSSDescriptor<E>.installAndApply() {
        while (true) {
            val curVal = array[index1].value
            when (curVal) {
                is DCSSDescriptor<*> -> curVal.apply()
                is CASDescriptor<*> -> curVal.apply()
                else -> {
                    if (array[index1].compareAndSet(expected1, this)){
                        apply()
                    }
                    break
                }
            }
        }
    }

    private fun <E> DCSSDescriptor<E>.apply(){
        if (expected2 == array[index2].value){
            status.compareAndSet(UNDECIDED, SUCCESS)
            array[index1].compareAndSet(this, update1)
        } else {
            status.compareAndSet(UNDECIDED, FAIL)
            array[index1].compareAndSet(this, expected1)
        }
    }
    class DCSSDescriptor<E>(
        val index1: Int, val expected1: E?, val update1: E?,
        val index2: Int, val expected2: E?
    ) {
        val status = atomic(UNDECIDED)

    }
    class CASDescriptor<E>(
        val index1: Int,
        val expected1: E,
        val update1: E,
    ) {
        val status = atomic(UNDECIDED)
    }
    enum class Status {
        UNDECIDED, SUCCESS, FAIL
    }
}


