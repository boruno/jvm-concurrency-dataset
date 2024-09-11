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

        while (true) {
            descriptor.apply()
            return descriptor.status.value == SUCCESS
        }
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

        fun apply(newStatus: Status) {
            // TODO: update the status, update the cells.
            when (newStatus) {
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

        fun apply() {
            while (true) {
                val maybeDescriptor1 = array[index1].value
                if (maybeDescriptor1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    maybeDescriptor1.help()
                } else {
                    if (array[index1].compareAndSet(expected1, this)) {
                        val maybeDescriptor2 = array[index2].value
                        if (maybeDescriptor2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                            maybeDescriptor2.help()
                        } else {
                            array[index2].compareAndSet(expected2, this)
                            setStatus()
                            return
                        }
                    } else {
                        setStatus()
                        return
                    }
                }
            }
        }

        private fun setStatus() {
            if (array[index1].value == this && array[index2].value == this) {
                apply(SUCCESS)
            } else {
                apply(FAILED)
            }
        }

        private fun help() {
            when (status.value) {
                SUCCESS, FAILED -> {
                    setStatus()
                }

                UNDECIDED -> {
                    array[index2].compareAndSet(expected2, this)
                    setStatus()
                }
            }

        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}