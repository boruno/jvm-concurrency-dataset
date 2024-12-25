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
        while (true) {
            val value = array[index].value
            when (value) {
                is AtomicArrayWithDCSS<*>.DCSSDescriptor -> {
                    value.update()
                    // TODO mb check status?
                    continue
                }
                else -> return value as E?
            }
        }
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        while (true) {
            val value = array[index].value
            when (value) {
                is AtomicArrayWithDCSS<*>.DCSSDescriptor -> {
                    value.update()
                    // TODO mb check status?
                    continue
                }
                else -> array[index].compareAndSet(expected, update)
            }
        }
    }

    fun dcss(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO Store a DCSS descriptor in array[index1].
        val desc = DCSSDescriptor(index1, expected1, update1, index2, expected2)
        desc.update()
        return desc.status.value === Status.SUCCESS
    }

    private fun tryPutDescriptor(index: Int, expected: E?): Boolean {
        while (true) {
            val value = array[index].value
            when {
                value === this -> {
                    return true
                }
                value is AtomicArrayWithDCSS<*>.DCSSDescriptor -> {
                    value.update()
                }
                value == expected -> {
                    if ( array[index].compareAndSet(expected, this) ) {
                        return true
                    } else {
                        continue
                    }
                }
                else -> {
                    return false
                }
            }
        }
    }

    inner class DCSSDescriptor(
        val index1: Int,
        val expected1: E?,
        val update1: E?,
        private val index2: Int,
        val expected2: E?
    ) {
        val status = atomic(Status.UNDECIDED)

        fun update() {
            if (status.value === Status.UNDECIDED) {
                if (!tryPutDescriptor(index1, expected1)) {
                    status.compareAndSet(Status.UNDECIDED, Status.FAILED)
                }

                if (array[index2].value == expected2) {
                    status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
                } else {
                    status.compareAndSet(Status.UNDECIDED, Status.FAILED)
                }
            }

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