//package day3

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

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
        val element = array[index].value
        return when (element) {
            is AtomicArrayWithDCSS<*>.DCSSDescriptor -> {
                when (element.status.value) {
                    Status.UNDECIDED, Status.FAILED -> element.expected1
                    Status.SUCCESS -> element.update1
                }
            }

            else -> element
        } as E
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        while (true) {
            when (val state = array[index].value) {
                is AtomicArrayWithDCSS<*>.DCSSDescriptor -> {
                    state.apply()
                }

                else -> {
                    return array[index].compareAndSet(expected, update)
                }
            }
        }
    }

    fun dcss(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }

        val descriptor = DCSSDescriptor(index1, expected1, update1, index2, expected2)
        descriptor.apply()
        return descriptor.status.value == Status.SUCCESS
    }

    inner class DCSSDescriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
    ) {
        val status = atomic(Status.UNDECIDED)

        fun apply() {
            if (status.value == Status.UNDECIDED) {
                val installed = tryInstallDescriptor()

                if (installed) {
                    val actual = readExpected()

                    val success = changeStatus(actual)
                    if (success) {
                        status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
                    } else {
                        status.compareAndSet(Status.UNDECIDED, Status.FAILED)

                    }
                }
            }

            updateValue()
        }

        private fun tryInstallDescriptor(): Boolean {
            while (true) {
                when (val state = array[index1].value) {
                    this -> {
                        return true
                    }

                    is AtomicArrayWithDCSS<*>.DCSSDescriptor -> {
                        state.apply()
                    }

                    expected1 -> {
                        if (array[index1].compareAndSet(expected1, this)) {
                            return true
                        } else {
                            continue
                        }
                    }

                    else -> { // value, not expected
                        return false
                    }
                }
            }
        }

        private fun readExpected(): E {
            while (true) {
                when (val state = array[index2].value) {
                    is AtomicArrayWithDCSS<*>.DCSSDescriptor -> {
                        state.apply()
                    }

                    else -> { // value, not expected
                        return state as E
                    }
                }
            }
        }

        private fun changeStatus(actual: E): Boolean {
            return if (expected2 == actual) {
                status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
            } else {
                status.compareAndSet(Status.UNDECIDED, Status.FAILED)
                false
            }

        }

        private fun updateValue() {
            when (status.value) {
                Status.SUCCESS -> array[index1].compareAndSet(this, update1)
                Status.FAILED -> array[index1].compareAndSet(this, expected1)
                else -> {}
            }

        }

    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}