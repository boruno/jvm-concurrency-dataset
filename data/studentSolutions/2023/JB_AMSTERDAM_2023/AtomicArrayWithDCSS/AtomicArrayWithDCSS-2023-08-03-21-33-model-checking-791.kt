package day3

import kotlinx.atomicfu.AtomicArray
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

// This implementation never stores `null` values.
class AtomicArrayWithDCSS<E : Any>(size: Int, initialValue: E) {
    private val array: AtomicArray<Any?> = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E? {
        val value = array[index].value
        return if (value is AtomicArrayWithDCSS<*>.DCSSDescriptor<*>)
            value.value as E?
        else
            value as E?
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        while (true) {
            if (array[index].compareAndSet(expected, update))
                return true

            val value = array[index].value
            if (value !is AtomicArrayWithDCSS<*>.DCSSDescriptor<*>) return false

            value.updateStatus()
            value.updateCell()
        }
    }

    fun dcss(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }

        val descriptor = DCSSDescriptor(index1, expected1, update1, index2, expected2)
        descriptor.apply()
        return descriptor.status.value === Status.SUCCESS
    }

    private inner class DCSSDescriptor<E>(
        private val index1: Int,
        private val expected1: E?,
        private val update1: E?,
        private val index2: Int,
        private val expected2: E?,
    ) {
        val status = atomic(Status.UNDECIDED)

        val value: E? get() =
            if (status.value == Status.SUCCESS)
                update1
            else
                expected1

        fun apply() {
            if (install()) {
                updateStatus()
                updateCell()
            }
        }

        private fun install(): Boolean {
            return array[index1].compareAndSet(expected1, this)
        }

        fun updateStatus() {
            val newStatus = if (array[index2].value === expected2) Status.SUCCESS else Status.FAILED
            status.compareAndSet(Status.UNDECIDED, newStatus)
        }

        fun updateCell() {
            when (status.value) {
                Status.UNDECIDED -> throw IllegalStateException()
                Status.SUCCESS -> array[index1].compareAndSet(this, update1)
                Status.FAILED -> array[index1].compareAndSet(this, expected1)
            }
        }
    }

    private enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}