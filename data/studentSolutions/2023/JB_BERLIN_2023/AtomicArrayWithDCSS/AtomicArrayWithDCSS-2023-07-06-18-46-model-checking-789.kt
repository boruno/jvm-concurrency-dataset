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

    fun get(index: Int): E {
        return when(val value =  array[index].value) {
            is AtomicArrayWithDCSS<*>.DCSSDescriptor -> {
                if (value.status.value == Status.SUCCESS) {
                    value.update1
                } else {
                    value.expected1
                }
            }
            else -> value
        } as E
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        while (true) {
            val x = array[index].value
            when {
                x is AtomicArrayWithDCSS<*>.DCSSDescriptor -> x.apply()
                x === expected -> {
                    if (array[index].compareAndSet(expected, update)) return true
                }
                else -> {
                    return false
                }
            }
        }
    }

    fun dcss(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }

        val descriptor = DCSSDescriptor(index1, index2, expected1, expected2, update1)
        descriptor.apply()

        return descriptor.status.value == Status.SUCCESS
    }

    inner class DCSSDescriptor(
        val index1: Int,
        val index2: Int,
        val expected1: E,
        val expected2: E,
        val update1: E
    ) {
        val status = atomic(Status.UNDECIDED)

        fun apply() {
            if (status.value == Status.UNDECIDED) {
                if (tryInstall() && afterCheck()) {
                    status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
                } else {
                    status.compareAndSet(Status.UNDECIDED, Status.FAILED)
                }
            }
            updateValue()
        }

        private fun afterCheck(): Boolean {
            while (true) {
                val x = array[index2].value

                when {
                    x is AtomicArrayWithDCSS<*>.DCSSDescriptor -> x.apply()
                    else -> return (x === expected2)
                }
            }
        }

        private fun tryInstall(): Boolean {
            while (true) {
                val x = array[index1].value

                when {
                    x === this -> return true
                    x is AtomicArrayWithDCSS<*>.DCSSDescriptor -> x.apply()
                    x === expected1 -> array[index1].compareAndSet(expected1, this)
                    else -> return false
                }
            }
        }

        private fun updateValue() {
            when(status.value) {
                Status.SUCCESS -> array[index1].compareAndSet(this, update1)
                else -> array[index1].compareAndSet(this, expected1)
            }
        }
    }



    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}