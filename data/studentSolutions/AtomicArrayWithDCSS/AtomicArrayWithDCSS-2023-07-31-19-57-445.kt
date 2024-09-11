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
        val value = array[index].value
        return if (value is DCSSDescriptor<*>) {
            if (value.status.value == Status.SUCCESS) {
                value.update1 as E?
            } else {
                value.expected1 as E?
            }
        } else {
            value as E?
        }
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        return array[index].compareAndSet(expected, update)
    }

    fun dcss(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO This implementation is not linearizable!
        // TODO Store a DCSS descriptor in array[index1].
//        if (array[index1].value != expected1 || array[index2].value != expected2) return false
        if (expected1 == update1) {
            return get(index2) == expected2
        }

        val desc = DCSSDescriptor(expected1, update1/*, expected2*/)
        if (!array[index1].compareAndSet(expected1, desc)) {
            return false
        }
        if (get(index2) == expected2) {
            desc.status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
        } else {
            desc.status.compareAndSet(Status.UNDECIDED, Status.FAILED)
        }
        return (desc.status.value == Status.SUCCESS).also {
            if (it) {
                array[index1].compareAndSet(desc, update1)
            } else {
                array[index1].compareAndSet(desc, expected1)
            }
        }
    }

    class DCSSDescriptor<E>(
        val expected1: E?,
        val update1: E?,
//        val expected2: E?,
        val status: AtomicRef<Status> = atomic(Status.UNDECIDED)
    )

    enum class Status {
        UNDECIDED,
        SUCCESS,
        FAILED
    }
}