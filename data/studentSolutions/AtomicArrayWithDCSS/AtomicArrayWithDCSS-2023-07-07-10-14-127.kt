//package day3

import kotlinx.atomicfu.*
import day3.AtomicArrayWithDCSS.Status.*

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
        while (true) {
            @Suppress("UNCHECKED_CAST")
            when (val value = array[index].value) {
                is AtomicArrayWithDCSS<*>.DCSSDescriptor -> value.apply()
                else -> return value as E
            }
        }
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        while (true) {
            when (val value = array[index].value) {
                is AtomicArrayWithDCSS<*>.DCSSDescriptor -> value.apply(help = true)

                expected -> {
                    if (array[index].compareAndSet(value, update)) {
                        return true
                    }
                }

                else -> return false
            }
        }
    }

    fun dcss(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E,
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }

        val descriptor = DCSSDescriptor(index1, expected1, update1, index2, expected2)
        descriptor.apply()
        return descriptor.status.value == SUCCESS
    }

    inner class DCSSDescriptor(
        private val index1: Int,
        val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E,
    ) {
        val status = atomic(UNDECIDED)

        fun apply(help: Boolean = false) {
            val installed = if (!help) {
                installDescriptor(index1, expected1)
            } else true

            updateStatus(installed)

            applyChanges()
        }

        private fun installDescriptor(index: Int, expected: E): Boolean {
            while (status.value == UNDECIDED) {
                when (val value = array[index].value) {
                    this -> break

                    is AtomicArrayWithDCSS<*>.DCSSDescriptor -> value.apply()

                    expected -> {
                        if (array[index].compareAndSet(value, this)) {
                            break
                        }
                    }

                    else -> return false
                }
            }

            return true
        }

        @Suppress("UNCHECKED_CAST")
        private fun updateStatus(installed: Boolean) {
            if (installed) {
                val actual = when (val value = array[index2].value) {
                    is AtomicArrayWithDCSS<*>.DCSSDescriptor -> value.expected1
                    else -> value as E
                }
                if (actual == expected2) {
                    status.compareAndSet(UNDECIDED, SUCCESS)
                } else {
                    status.compareAndSet(UNDECIDED, FAILED)
                }
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }
        }

        private fun applyChanges() {
            if (status.value == SUCCESS) {
                array[index1].compareAndSet(this, update1)
            } else { // FAILED
                array[index1].compareAndSet(this, expected1)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}