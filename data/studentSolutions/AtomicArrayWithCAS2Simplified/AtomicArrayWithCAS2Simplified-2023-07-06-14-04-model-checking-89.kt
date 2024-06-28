@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls


// This implementation never stores `null` values.
class AtomicArrayWithCAS2Simplified<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E {
        // TODO: the cell can store CAS2Descriptor
        val maybeDescriptor = array[index].value
        return if (maybeDescriptor is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            when (maybeDescriptor.status.value) {
                UNDECIDED, FAILED -> {
                    if (index == maybeDescriptor.index1) {
                        maybeDescriptor.expected1
                    } else {
                        maybeDescriptor.expected2
                    }
                }

                SUCCESS -> {
                    if (index == maybeDescriptor.index1) {
                        maybeDescriptor.update1
                    } else {
                        maybeDescriptor.update2
                    }
                }
            }
        } else {
            maybeDescriptor
        } as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: use CAS2Descriptor to fix it.

        val descriptor = if (index1 < index2) {
            CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        } else {
            CAS2Descriptor(index2, expected2, update2, index1, expected1, update1)
        }

        descriptor.apply()
        return descriptor.status.value == SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            val success = tryInstallDescriptor()

            if (success) {
                status.compareAndSet(UNDECIDED, SUCCESS)
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }

            updateValues()
        }

        private fun tryInstallDescriptor(): Boolean {
            return tryInstallDescriptor(index1, expected1) && tryInstallDescriptor(index2, expected2)
        }

        private fun tryInstallDescriptor(index: Int, expected: E): Boolean {
            while (true) {
                when (val state = array[index].value) {
                    this -> {
                        return true
                    }

                    is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
                        if (state.status.value == UNDECIDED) {
                            state.apply()
                        }
                    }

                    expected -> {
                        if (array[index].compareAndSet(expected, this)) {
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

        private fun updateValues() {
            // TODO: update the status, update the cells.
            when (status.value) {
                SUCCESS -> {
                    array[index1].compareAndSet(this, update1)
                    array[index2].compareAndSet(this, update2)
                }

                FAILED -> {
                    array[index1].compareAndSet(this, expected1)
                    array[index2].compareAndSet(this, expected2)
                }

                else -> {}
            }

        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}