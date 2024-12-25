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

        if (value is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
            return if (value.status.value == Status.SUCCESS) {
                value.update1 as E?
            } else {
                value.expected1 as E?
            }
        }
        return value as E?
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        // TODO: the cell can store a descriptor
        while (true) {
            when (val value = array[index].value) {
                is AtomicArrayWithDCSS<*>.DCSSDescriptor -> when (value.status.value) {
                    Status.UNDECIDED -> value.apply()
                    Status.SUCCESS -> updateValue(value, true)
                    Status.FAILED -> updateValue(value, false)
                }
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
        // TODO This implementation is not linearizable!
        // TODO Store a DCSS descriptor in array[index1].
        // if (array[index1].value != expected1 || array[index2].value != expected2) return false
        // array[index1].value = update1

        val descriptor = DCSSDescriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2
        )
        descriptor.apply()
        return descriptor.status.value === Status.SUCCESS
    }

    inner class DCSSDescriptor(
        val index1: Int,
        val expected1: E?,
        val update1: E?,
        val index2: Int,
        val expected2: E?
    ) {
        val status = atomic(Status.UNDECIDED)

        fun apply() {
            if (install() && get(index2) == expected2 && updateStatus(Status.SUCCESS)) {
                updateValue(this, true)
            } else if (updateStatus(Status.FAILED)) {
                updateValue(this, false)
            }
        }

        private fun install(): Boolean {
            while (true) {
                when (val value = array[index1].value) {
                    this -> return true

                    is AtomicArrayWithDCSS<*>.DCSSDescriptor -> when (value.status.value) {
                        Status.UNDECIDED -> if (get(value.index2) == value.expected2 && value.updateStatus(Status.SUCCESS)) {
                            updateValue(value, true)
                        } else if (updateStatus(Status.FAILED)) {
                            updateValue(value, false)
                        }
                        Status.SUCCESS -> updateValue(value, true)
                        Status.FAILED -> updateValue(value, false)
                    }
                    expected1 -> {
                        if (array[index1].compareAndSet(expected1, this)) return true
                    }
                    else -> return false
                }
            }
        }

        private fun updateStatus(value: Status): Boolean {
            return status.compareAndSet(Status.UNDECIDED, value)
        }
    }

    private fun updateValue(desc: AtomicArrayWithDCSS<*>.DCSSDescriptor, success: Boolean) {
        if (success) {
            array[desc.index1].compareAndSet(desc, desc.update1)
        } else {
            array[desc.index1].compareAndSet(desc, desc.expected1)
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}