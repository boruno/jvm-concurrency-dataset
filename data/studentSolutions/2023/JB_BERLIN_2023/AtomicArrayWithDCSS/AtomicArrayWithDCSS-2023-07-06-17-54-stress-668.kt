package day3

import day3.AtomicArrayWithDCSS.Status.*
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
        val value = array[index].value
        if (value is AtomicArrayWithDCSS<*>.Descriptor) {
            return if (value.status.value !== SUCCESS) value.expected1 as E else value.update1 as E
        }
        return value as E
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        // TODO: the cell can store a descriptor
        TODO("Not implemented")
        return array[index].compareAndSet(expected, update)
    }

    fun dcss(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }

        val descriptor = Descriptor(index1, expected1, update1, index2, expected2)
        descriptor.apply()
        return descriptor.status.value == SUCCESS
    }

    inner class Descriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            if (status.value === UNDECIDED) {
                val success = tryInstallDescriptor(index1, expected1)
                if (success) {
                    if (array[index2].value == expected2) {
                        status.compareAndSet(UNDECIDED, SUCCESS)
                    }
                    else {
                        status.compareAndSet(UNDECIDED, FAILED)
                    }
                }
                else {
                    status.compareAndSet(UNDECIDED, FAILED)
                }
            }
            updateValue()
        }

        private fun tryInstallDescriptor(index: Int, expected: E): Boolean {
            while (true) {
                when (val item = array[index].value) {
                    this -> {
                        return true // already installed
                    }
                    is AtomicArrayWithDCSS<*>.Descriptor -> {
                        item.apply()
                    }
                    expected -> {
                        if (array[index].compareAndSet(expected, this)) {
                            return true
                        }
                        else {
                            continue
                        }
                    }
                    else -> {
                        return false
                    }
                }
            }
        }

        private fun updateValue() {
            if (status.value === SUCCESS) {
                array[index1].compareAndSet(this, update1)
            }
            else {
                array[index1].compareAndSet(this, expected1)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }

}