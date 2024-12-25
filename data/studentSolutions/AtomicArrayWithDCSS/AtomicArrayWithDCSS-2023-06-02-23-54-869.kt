//package day4

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

    @Suppress("UNCHECKED_CAST")
    operator fun get(index: Int): E? {
        while (true) {
            val res =  array[index].value
            if (res is AtomicArrayWithDCSS<*>.DescriptorDCSS) {
                res.applyOperation()
                continue
            }
            else return res as E?
        }
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        while (true) {
            val res = array[index].value
            if (res is AtomicArrayWithDCSS<*>.DescriptorDCSS) {
                res.applyOperation()
                continue
            }
            if (res != expected) return false
            if (array[index].compareAndSet(expected, update)) return true
        }
    }

    fun dcss(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }

        while (true) {
            val val1 = array[index1].value
            val val2 = array[index2].value

            if (val1 is AtomicArrayWithDCSS<*>.DescriptorDCSS) {
                val1.applyOperation()
                continue
            }

            if (val2 is AtomicArrayWithDCSS<*>.DescriptorDCSS) {
                val2.applyOperation()
                continue
            }

            if (val1 != expected1 || val2 != expected2) return false

            val descriptorDCSS = DescriptorDCSS(index1, index2, expected1, expected2, update1)
            descriptorDCSS.applyOperation()

            if (descriptorDCSS.status.value == SUCCESS) return true
        }
    }


    private inner class DescriptorDCSS(
        val index1: Int,
        val index2: Int,
        val expected1: E?,
        val expected2: E?,
        val update: E?
    ) {
        val status = atomic(WAITING)

        @Suppress("EMPTY_IF")
        fun applyOperation() {
            if (status.value == SUCCESS) {
                array[index1].compareAndSet(this, update)
                return
            }
            if (status.value == FAILURE) {
                array[index1].compareAndSet(this, expected1)
                return
            }
            if (!array[index1].compareAndSet(expected1, this) && array[index1].value != this) {
                if (status.compareAndSet(WAITING, FAILURE)) {
                    array[index1].compareAndSet(this, expected1)
                    return
                }
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
    private enum class Status {
        WAITING, SUCCESS, FAILURE
    }


}

