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
        val value = array[index].value
        if (value !is AtomicArrayWithDCSS<*>.DcssDescriptor) return value as E?
        while (true) {
            when (value.status.value) {
                Status.UNDECIDED -> {
                    value.updateStatus()
                    value.updateValue()
                }
                Status.FAILED -> return value.expected1 as E?
                Status.SUCCESS -> return value.update1 as E?
            }
        }
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        // TODO: the cell can store a descriptor
        while (true) {
            when (val value = array[index].value) {
                expected -> if (array[index].compareAndSet(expected, update)) return true
                is AtomicArrayWithDCSS<*>.DcssDescriptor -> {
                    value.updateStatus()
                    value.updateValue()
                }
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
        val desc = DcssDescriptor(index1, expected1, update1, index2, expected2)
        desc.apply()
        return desc.status.value == Status.SUCCESS
    }

    private inner class DcssDescriptor(
        val index1: Int,
        val expected1: E?,
        val update1: E?,
        val index2: Int,
        val expected2: E?,
    ) {
        val status = atomic(Status.UNDECIDED)

        fun apply() {
            if (!install()) return
            updateStatus()
            updateValue()
        }

        fun install(): Boolean {
            while (true) {
                when (val current1 = array[index1].value) {
                    this -> break
                    expected1 -> if (array[index1].compareAndSet(expected1, this)) break
                    is AtomicArrayWithDCSS<*>.DcssDescriptor -> {
                        current1.updateStatus()
                        current1.updateValue()
                    }
                    else -> return false
                }
            }
            return true
        }

        fun updateStatus(caller: DcssDescriptor = this) {
            if (caller == this) return
            if (get(index2) == expected2) {
                status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
            } else {
                status.compareAndSet(Status.UNDECIDED, Status.FAILED)
            }
        }

        fun updateValue() {
            when (status.value) {
                Status.UNDECIDED -> assert(false)
                Status.SUCCESS -> array[index1].compareAndSet(this, update1)
                Status.FAILED -> array[index1].compareAndSet(this, expected1)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}