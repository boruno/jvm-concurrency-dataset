//package day3

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
        while (true) {
            val value = array[index].value
            when {
                value is AtomicArrayWithDCSS<*>.DCSSDescriptor -> value.apply()
                else -> value as E
            }
        }
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        // TODO: the cell can store a descriptor
        while (true) {
            val value = array[index].value
            when {
                value is AtomicArrayWithDCSS<*>.DCSSDescriptor -> value.apply()
                else -> {
                    if (value != expected) return false
                    if (array[index].compareAndSet(expected, update)) return true //else go to next try
                }
            }
        }
    }

    fun dcss(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val dcss = DCSSDescriptor(
            index1, expected1, update1,
            index2, expected2
        )
        dcss.apply()
        return dcss.status.value == Status.SUCCESS
    }

    inner class DCSSDescriptor(
        val index: Int, val expected: E?, val update: E?,
        val checkIndex: Int, val checkExpectedValue: E?
    ) {
        val status = atomic(Status.UNDECIDED)

        private fun fail() {
            status.compareAndSet(Status.UNDECIDED, Status.FAILED)
        }

        private fun success() {
            status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
        }


        fun apply() {
            setDcss()
            if (status.value == Status.SUCCESS) {
                array[index].compareAndSet(this, update)
            } else {
                array[index].compareAndSet(this, expected)
            }
        }

        private fun setDcss() {
            if (array[checkIndex].value != checkExpectedValue) return fail()
            while (true) {
                val value = array[index].value
                when {
                    value === this -> return success()
//                    value is AtomicArrayWithDCSS<*>.CAS2Descriptor -> value.finish()
                    value is AtomicArrayWithDCSS<*>.DCSSDescriptor -> value.apply()
                    else -> {
                        if (value != expected) return fail()
                        if (array[index].value == expected) {
                            if (array[index].compareAndSet(expected, this)) return success()
                            //else run again
                        } else return fail()
                    }
                }
            }
        }



    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}