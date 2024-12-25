//package day3

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

    fun get(index: Int): E? {
        // TODO: the cell can store a descriptor
        val value = array[index].value
        val result: E? = if (value is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
            value.getValue() as E?
        } else {
            return value as E?
        }
        return result
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        // TODO: the cell can store a descriptor
        while (true) {
            when (val value = array[index].value) {
                // expected value, try to set
                expected -> {
                    if (array[index].compareAndSet(expected, update)) {
                        // set successfully
                        return true
                    } else {
                        // failed, try again
                        continue
                    }
                }
                // contains a descriptor, help
                is AtomicArrayWithDCSS<*>.DCSSDescriptor -> value.help()
                // unexpected value
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
        val descriptor = DCSSDescriptor(index1, expected1, update1, index2, expected2)
        descriptor.apply()
        return descriptor.status.value == SUCCESS
    }

    inner class DCSSDescriptor(
        private val index1: Int,
        private val expected1: E?,
        private val update1: E?,
        private val index2: Int,
        private val expected2: E?
    ) {
        val status = atomic(UNDECIDED)

        fun getValue(): E? {
            return if (status.value == SUCCESS) {
                update1
            } else {
                expected1
            }
        }

        fun apply() {
            val installed = install()
            updateStatus(installed)
            updateCell()
        }

        fun help() {
            updateStatus(true)
            updateCell()
        }

        private fun install(): Boolean {
            while (true) {
                if (status.value != UNDECIDED) return false

                when (val value = array[index1].value) {
                    // expected value, try to install the descriptor
                    expected1 -> {
                        if (array[index1].compareAndSet(expected1, this)) {
                            // installed successfully
                            return true
                        } else {
                            // failed, try again
                            continue
                        }
                    }
                    // the descriptor is already installed
                    this -> return true
                    // another descriptor, help and try again
                    is AtomicArrayWithDCSS<*>.DCSSDescriptor -> value.help()
                    // unexpected value
                    else -> return false
                }
            }
        }

        private fun secondValueExpected(): Boolean {
            while (true) {
                if (status.value != UNDECIDED) return false

                when (val value = array[index2].value) {
                    // expected value
                    expected2 -> return true
                    // contains a descriptor, help and try again
                    is AtomicArrayWithDCSS<*>.DCSSDescriptor -> value.help()
                    // unexpected value
                    else -> return false
                }
            }
        }

        private fun updateStatus(installed: Boolean) {
            if (installed && secondValueExpected()) {
                status.compareAndSet(UNDECIDED, SUCCESS)
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }
        }

        private fun updateCell() {
            if (status.value == SUCCESS) {
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