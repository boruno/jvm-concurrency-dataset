package day3

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
        // TODO: the cell can store CAS2Descriptor
        val value = array[index].value
        if (value is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
            return when (value.status2.value) {
                day3.AtomicArrayWithDCSS.Status.UNDECIDED -> value.expected1 as? E
                day3.AtomicArrayWithDCSS.Status.SUCCESS -> value.update1 as? E
                day3.AtomicArrayWithDCSS.Status.FAILED -> value.expected1 as? E
            }
        }

        return value as? E
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        while (true) {
            val value = array[index].value
            if (value is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
                value.apply()
                continue
            }

            if (value != expected) return false
            if (array[index].compareAndSet(expected, update))
                return true
        }
    }

    fun dcss(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }

//        // TODO This implementation is not linearizable!
//        // TODO Store a DCSS descriptor in array[index1].
        if (array[index1].value != expected1 || array[index2].value != expected2) return false

        val d = DCSSDescriptor(index1, expected1, update1, index2, expected2)
        d.apply()
        return d.status2.value == Status.SUCCESS
    }


    inner class DCSSDescriptor(
        val index1: Int, val expected1: E?, val update1: E?, val index2: Int, val expected2: E?
    ) {
        val status2 = atomic(Status.UNDECIDED)

        fun apply() {
            while (true) {
                val localStatus = status2.value

                if (localStatus == Status.SUCCESS) {
                    array[index1].compareAndSet(this, update1)
                    return
                }

                if (localStatus == Status.FAILED) {
                    array[index1].compareAndSet(this, expected1)
                    return
                }

                if (!array[index1].compareAndSet(expected1, this)) {
                    status2.compareAndSet(Status.UNDECIDED, Status.FAILED)
                    continue
                }

                if (array[index2].value != expected2) {
                    status2.compareAndSet(Status.UNDECIDED, Status.FAILED)
                    continue
                }

                status2.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}