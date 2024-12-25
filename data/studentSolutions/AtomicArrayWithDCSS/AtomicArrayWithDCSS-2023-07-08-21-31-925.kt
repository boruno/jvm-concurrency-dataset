//package day3

import day3.AtomicArrayWithDCSS.Status.*
import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class AtomicArrayWithDCSS<E : Any>(size: Int, initialValue: E) {
    val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E? = when (val value = array[index].value) {
            is AtomicArrayWithDCSS<*>.DCSSDescriptor<*> -> when ((value as DCSSDescriptor<E>).status.value) {
                UNDECIDED, FAIL -> value.expected1
                SUCCESS -> value.update1
            }

            is AtomicArrayWithDCSS<*>.CASDescriptor<*> -> when ((value as CASDescriptor<E>).status.value) {
                UNDECIDED, FAIL -> value.expected1
                SUCCESS -> value.update1
            }

            else -> value as E?
        }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        val desc = CASDescriptor(index, expected, update)
        desc.installAndApply()
        return desc.result
    }

    fun dcss(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val desc = DCSSDescriptor(index1, expected1, update1, index2, expected2)
        desc.installAndApply()
        return desc.result
    }


    interface Descriptor {
        fun installAndApply()
        fun invoke()
        val result: Boolean
    }

    inner class DCSSDescriptor<E>(
        val index1: Int, val expected1: E?, val update1: E?,
        val index2: Int, val expected2: E?
    ) : Descriptor {
        val status = atomic(UNDECIDED)
        override fun installAndApply() {
            while (true) {
                if (array[index1].compareAndSet(expected1, this)) {
                    invoke()
                    return
                }
                when (val curVal = array[index1].value) {
                    is Descriptor -> curVal.invoke()
                    else -> {
                        if (array[index1].compareAndSet(expected1, this)) invoke()
                        return
                    }
                }
            }
        }


        override fun invoke() {
            if (expected2 == array[index2].value) {
                status.compareAndSet(UNDECIDED, SUCCESS)
                array[index1].compareAndSet(this, update1)
            } else {
                status.compareAndSet(UNDECIDED, FAIL)
                array[index1].compareAndSet(this, expected1)
            }
        }

        override val result get() = status.value == SUCCESS

    }

    inner class CASDescriptor<E>(val index1: Int, val expected1: E, val update1: E) : Descriptor {
        val status = atomic(UNDECIDED)
        override fun installAndApply() {
            while (true) {
                if (array[index1].compareAndSet(expected1, this)) {
                    invoke()
                    return
                }
                when (val curVal = array[index1].value) {
                    is Descriptor -> curVal.invoke()
                    else -> {
                        if (array[index1].compareAndSet(expected1, this)) invoke()
                        return
                    }
                }
            }
        }

        override fun invoke() {
            status.compareAndSet(UNDECIDED, SUCCESS)
            array[index1].compareAndSet(null, update1)
        }

        override val result get() = status.value == SUCCESS
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAIL
    }
}


