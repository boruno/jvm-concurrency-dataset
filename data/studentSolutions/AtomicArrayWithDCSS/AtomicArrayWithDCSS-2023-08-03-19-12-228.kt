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
        val value = array[index].value
        return when (value) {
            is AtomicArrayWithDCSS<*>.DCSSDescriptor -> return when (value.status.value) {
                Status.SUCCESS -> value.update1 as E?
                else -> value.expected1 as E?
            }
            else -> value as E?
        }
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        while (!array[index].compareAndSet(expected, update)) {
            val actual = array[index].value
            if (actual is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
                if (actual.status.value == Status.UNDECIDED)
                    actual.apply()
                else
                    actual.updateCellsSuccessOrFailed()
                continue
            }

            if (actual != expected)
                return false
        }

        return true
    }

    fun dcss(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO This implementation is not linearizable!
        // TODO Store a DCSS descriptor in array[index1].
        val descriptor = DCSSDescriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2
        )
        descriptor.apply()
        return descriptor.status.value === Status.SUCCESS
    }

    inner class DCSSDescriptor(val index1: Int,
                               val expected1: E?,
                               val update1: E?,
                               val index2: Int,
                               val expected2: E?) {
        val status = atomic(Status.UNDECIDED)

        fun apply() {
            while (!array[index1].compareAndSet(expected1, this)) {
                val actual1 = array[index1].value
                if (actual1 is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
                    if (actual1 == this) break
                    if (actual1.status.value == Status.UNDECIDED)
                        actual1.apply()
                    else
                        actual1.updateCellsSuccessOrFailed()
                }
                else if (actual1 != expected1) {
                    status.compareAndSet(Status.UNDECIDED, Status.FAILED)
                    break
                }
            }

            if (array[index2].value != expected2)
            {
                status.compareAndSet(Status.UNDECIDED, Status.FAILED)
            }

            status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
            updateCellsSuccessOrFailed()
        }

        fun updateCellsSuccessOrFailed() {
            check(status.value == Status.SUCCESS || status.value == Status.FAILED)
            if (status.value == Status.SUCCESS) {
                array[index1].compareAndSet(this, update1)
            } else {
                array[index1].compareAndSet(this, expected1)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}