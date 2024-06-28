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
        while (true) {
            when (val value = array[index].value) {
                is AtomicArrayWithDCSS<*>.DCSSDescriptor -> value.proceed()
                else -> @Suppress("UNCHECKED_CAST") return value as E?
            }
        }
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        while (true) {
            when (val value = array[index].value) {
                is AtomicArrayWithDCSS<*>.DCSSDescriptor -> value.proceed()
                expected -> if (array[index].compareAndSet(expected, update)) return true
                else -> return false
            }
        }
    }

    fun dcss(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }

        val descriptor = DCSSDescriptor(index1, expected1, update1, index2, expected2)
        while (true) {
            when (val value = array[index1].value) {
                is AtomicArrayWithDCSS<*>.DCSSDescriptor -> value.proceed()
                expected1 -> if (array[index1].compareAndSet(expected1, descriptor)) break
                else -> return false
            }
        }

        descriptor.proceed()
        return descriptor.statusRef.value == Status.SUCCESS
    }

    private inner class DCSSDescriptor(
        private val index1: Int,
        private val expected1: E?,
        private val update1: E?,
        private val index2: Int,
        private val expected2: E?
    ) {
        val statusRef = atomic(Status.UNDECIDED)

        fun proceed() {
            while (true) {
                when (val currentStatus = statusRef.value) {
                    Status.SUCCESS -> {
                        array[index1].compareAndSet(this, update1)
                        return
                    }

                    Status.FAILED -> {
                        array[index1].compareAndSet(this, expected1)
                        return
                    }

                    Status.UNDECIDED -> {
                        val newStatus = if (array[index2].value != expected2) Status.FAILED else Status.SUCCESS
                        statusRef.compareAndSet(currentStatus, newStatus)
                    }
                }
            }
        }
    }

    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}