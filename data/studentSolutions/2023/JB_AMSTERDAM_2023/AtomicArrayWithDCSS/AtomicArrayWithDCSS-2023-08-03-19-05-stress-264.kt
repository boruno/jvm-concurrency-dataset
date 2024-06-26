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
        // TODO: the cell can store a descriptor
        val sell = array[index]
        while (true) {
            val value = sell.value
            when {
                value !is AtomicArrayWithDCSS<*>.CAS2Descriptor -> return value as E
                else -> value.apply()
            }
        }
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        // TODO: the cell can store a descriptor
        val sell = array[index]
        if (sell.compareAndSet(expected, update)) return true

        while (true) {
            val value = sell.value
            when {
                value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> value.apply()
                value != expected -> return false
                sell.compareAndSet(expected, this) -> return true
                else -> continue
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
        val descr = CAS2Descriptor(index1, expected1, update1, index2, expected2)
        descr.apply()
//        if (array[index1].value != expected1 || array[index2].value != expected2) return false
//        array[index1].value = update1
        return descr.status.value == Status.SUCCESS
    }

    inner class CAS2Descriptor(
        val originalIndex1: Int,
        expected1: E?,
        val update1: E?,
        index2: Int,
        expected2: E?,
    ) {
        private val index1: Int = if (originalIndex1 <= index2) originalIndex1 else index2
        private val expected1: E? = if (index1 <= index2) expected1 else expected2
        private val index2: Int = if (index1 <= index2) index2 else index1
        private val expected2: E? = if (index1 <= index2) expected2 else expected1

        val status = atomic(Status.UNKNOWN)

        fun read(index: Int): E? {
            val value = status.value
            return when {
                index == originalIndex1 && value == Status.SUCCESS -> update1
                else -> if (index == index1) expected1 else expected2
            }
        }

        fun apply() {
            val isSuccess = installDescriptor(index1, expected1, false) && installDescriptor(index2, expected2, true)
            updateStatus(isSuccess)
            updateCell(index1, read(index1))
            updateCell(index2, read(index2))
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
        }

        fun installDescriptor(index: Int, expected: E?, second: Boolean): Boolean {
            if (second && !status.compareAndSet(Status.UNKNOWN, Status.UNDECIDED)) {
                return false
            }

            when (status.value) {
                Status.UNDECIDED, Status.UNKNOWN -> {}
                Status.SUCCESS -> return true
                Status.FAILED -> return false
            }

            val sell = array[index]
            if (sell.compareAndSet(expected, this)) return true

            while (true) {
                val value = sell.value
                when {
                    value === this -> return true
                    value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> value.apply()
                    value != expected -> return false
                    sell.compareAndSet(expected, this) -> return true
                    else -> continue
                }
            }
        }

        fun updateStatus(isSuccess: Boolean) {
            val update = if (isSuccess) Status.SUCCESS else Status.FAILED
            status.compareAndSet(Status.UNDECIDED, update)
        }

        fun updateCell(index: Int, updatedValue: E?) {
            val sell = array[index]
            sell.compareAndSet(this, updatedValue)
        }
    }

    enum class Status {
        UNKNOWN, UNDECIDED, SUCCESS, FAILED
    }
}