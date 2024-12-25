//package day3

import day3.AtomicArrayWithDCSS.Status.*
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
            value is CAS2Descriptor<*>->when((value as CAS2Descriptor<E>).status.value){
                UNDECIDED, FAIL->if (index==value.index1) value.expected1 else value.expected2
                SUCCESS->if (index==value.index1) value.update1 else value.update2
            }
            else -> value as E?
        }
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        // TODO: the cell can store a descriptor
        while (true) {
            val curVal = array[index].value
            when (curVal) {
                is DCSSDescriptor<*> -> curVal.apply()
                else -> return array[index].compareAndSet(expected, update)
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
        desc.insallAndApply()
        return desc.status.value == SUCCESS
    }

    private fun <E> DCSSDescriptor<E>.insallAndApply() {
        while (true){
            if (array[index1].compareAndSet(expected1, this)){
                this.apply()
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
    class CAS2Descriptor<E>(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = atomic(UNDECIDED)
    }
    enum class Status {
        UNDECIDED, SUCCESS, FAIL
    }
}

