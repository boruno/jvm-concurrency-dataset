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
        return if (value is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
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
        if (expected == update) {
            return get(index) == expected
        }

        while (true) {
            val value = array[index].value
            if (value is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
                value.apply()
                value.remove()
            }
            if (array[index].compareAndSet(expected, update)) {
                return true
            }
            val newValue = array[index].value
            if (value == newValue && newValue !is AtomicArrayWithDCSS<*>) {
                return false
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
//        if (array[index1].value != expected1 || array[index2].value != expected2) return false

        if (expected1 == update1) {
            return get(index2) == expected2 && get(index1) == expected1
        }
        val desc = DCSSDescriptor(index1, expected1, update1, index2, expected2)
        if (!array[index1].compareAndSet(expected1, desc)) {
            return false
        }
        desc.apply()
        return desc.remove()
    }

    inner class DCSSDescriptor(
        val index1: Int,
        val expected1: E?,
        val update1: E?,
        val index2 : Int,
        val expected2: E?,
        val status: AtomicRef<Status> = atomic(Status.UNDECIDED)
    ) {
        fun apply() {
            if (get(index2) == expected2) {
                status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
            } else {
                status.compareAndSet(Status.UNDECIDED, Status.FAILED)
            }
        }

        fun remove() : Boolean {
            return if (status.value == Status.SUCCESS) {
                array[index1].compareAndSet(this, update1)
                true
            } else {
                array[index1].compareAndSet(this, expected1)
                false
            }
        }
    }

    enum class Status {
        UNDECIDED,
        SUCCESS,
        FAILED
    }
}