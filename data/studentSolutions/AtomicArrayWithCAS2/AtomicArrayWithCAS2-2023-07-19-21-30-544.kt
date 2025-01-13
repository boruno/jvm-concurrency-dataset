@file:Suppress("DuplicatedCode")

//package day4

import kotlinx.atomicfu.*

import AtomicArrayWithCAS2.Status.*

// This implementation never stores `null` values.
@Suppress("DUPLICATES")
class AtomicArrayWithCAS2<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E? {
        while (true) {
            val res =  array[index].value
            if (res is AtomicArrayWithCAS2<*>.Descriptor) {
                res.applyOperation()
                continue
            }
            else return res as E?
        }
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        while (true) {
            val res = array[index].value
            if (res is AtomicArrayWithCAS2<*>.Descriptor) {
                res.applyOperation()
                continue
            }
            if (res != expected) return false
            if (array[index].compareAndSet(expected, update)) return true
        }
    }

    fun cas2(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?, update2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        while (true) {
            val val1 = array[index1].value
            val val2 = array[index2].value

            if (val1 is AtomicArrayWithCAS2<*>.Descriptor) {
                val1.applyOperation()
                continue
            }

            if (val2 is AtomicArrayWithCAS2<*>.Descriptor) {
                val2.applyOperation()
                continue
            }

            if (val1 != expected1 || val2 != expected2) return false

            val descriptorCAS2 = DescriptorCAS(index1, expected1, update1, index2, expected2, update2)

            if (array[index1].compareAndSet(expected1, descriptorCAS2)) {
                descriptorCAS2.applyOperation()
            } else {
                descriptorCAS2.status.compareAndSet(WAITING, FAILURE)
            }

            if (descriptorCAS2.status.value == SUCCESS) return true
        }
    }

    fun dcss(
        index1 : Int, expected1 : E?, update1: DescriptorCAS,
        index2 : Int, expected2 : DescriptorCAS
    ) : Boolean {
        require(index1 != index2)

        while (true) {
            val val1 = array[index1].value
            val val2 = array[index2].value

            if (val1 is AtomicArrayWithCAS2<*>.Descriptor) {
                val1.applyOperation()
                continue
            }

            if (val2 is AtomicArrayWithCAS2<*>.DescriptorDCSS) {
                val2.applyOperation()
                continue
            }

            if (val1 != expected1 || val2 != expected2) return false

            val descriptorDCSS = DescriptorDCSS(index1, expected1, update1, index2, expected2)
            if (array[index1].compareAndSet(expected1, descriptorDCSS))
                descriptorDCSS.applyOperation()
            else
                descriptorDCSS.status.compareAndSet(WAITING, FAILURE)
            if (descriptorDCSS.status.value == SUCCESS) return true
        }
    }

    abstract inner class Descriptor {
        abstract fun applyOperation()
    }

    inner class DescriptorCAS(
        val index1 : Int, val expected1 : E?, val update1 : E?,
        val index2 : Int, val expected2 : E?, val update2 : E?
    ) : Descriptor() {

        val status = atomic(WAITING)
        override fun applyOperation() {
            if (status.value == SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
                return
            }
            if (status.value == FAILURE) {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
                return
            }
            if (!dcss(index2, expected2, this, index1, this) && array[index2].value != this) {
                if (status.compareAndSet(WAITING, FAILURE)) {
                    array[index1].compareAndSet(this, expected1)
                    array[index2].compareAndSet(this, expected2)
                    return
                }
            }
            if (status.compareAndSet(WAITING, SUCCESS)) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
                return
            }
            if (status.value == SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
                return
            }
            if (status.value == FAILURE) {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
                return
            }
        }
    }

    inner class DescriptorDCSS(
        val index1: Int, val expected1: E?, val update: DescriptorCAS,
        val index2: Int, val expected2: DescriptorCAS
    )  : Descriptor() {

        val status = atomic(WAITING)
        override fun applyOperation() {
            if (status.value == SUCCESS) {
                array[index1].compareAndSet(this, update)
                return
            }
            if (status.value == FAILURE) {
                array[index1].compareAndSet(this, expected1)
                return
            }
            if (array[index2].value != expected2) {
                if (status.compareAndSet(WAITING, FAILURE)) {
                    array[index1].compareAndSet(this, expected1)
                    return
                }
            }
            if (status.compareAndSet(WAITING, SUCCESS)) {
                array[index1].compareAndSet(this, update)
                return
            }
            if (status.value == SUCCESS) {
                array[index1].compareAndSet(this, update)
                return
            }
            if (status.value == FAILURE) {
                array[index1].compareAndSet(this, expected1)
                return
            }
        }
    }

    enum class Status{
        SUCCESS, WAITING, FAILURE
    }
}


