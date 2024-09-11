package day4

import kotlinx.atomicfu.*

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
        val curValue = array[index].value

        return if (curValue is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
            if (curValue.status.value == Status.SUCCESS) {
                curValue.update1 as E?
            } else {
                curValue.expected1 as E?
            }
        } else {
            curValue as E?
        }
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        val curValue = array[index].value

        if (curValue is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
            curValue.apply()
        }

        return array[index].compareAndSet(expected, update)
    }

    fun dcss(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO This implementation is not linearizable!
        // TODO Store a DCSS descriptor in array[index1].
        val descriptor = DCSSDescriptor(index1, expected1, update1, index2, expected2)

        val curValue = array[index1].value

        if (curValue is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
            curValue.apply()
        }

        if (!array[index1].compareAndSet(expected1, descriptor)) {
            return false
        }

        descriptor.apply()

        return descriptor.status.value == Status.SUCCESS
    }

    private inner class DCSSDescriptor(
        val index1: Int, val expected1: E?, val update1: E?,
        val index2: Int, val expected2: E?
    ) {
        val status = atomic(Status.UNDECIDED)

        fun apply() {
            when (val curStatus = status.value) {
                Status.UNDECIDED -> {
                    if (array[index2].value == expected2) {
                        status.compareAndSet(curStatus, Status.SUCCESS)
                    } else {
                        status.compareAndSet(curStatus, Status.FAILED)
                    }

                    apply()
                }
                Status.SUCCESS -> {
                    array[index1].compareAndSet(this, update1)
                }
                Status.FAILED -> {
                    array[index1].compareAndSet(this, expected1)
                }
            }
        }
    }

    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}