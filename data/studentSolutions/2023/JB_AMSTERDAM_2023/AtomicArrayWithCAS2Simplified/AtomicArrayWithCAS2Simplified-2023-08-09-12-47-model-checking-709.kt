@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import java.util.concurrent.atomic.*


// This implementation never stores `null` values.
class AtomicArrayWithCAS2Simplified<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    fun get(index: Int): E {
        val value = array[index]

        if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            val status = value.status.get()

            if (status == FAILED || status == UNDECIDED) {
                return if (value.index1 == index) {
                    value.expected1 as E
                } else {
                    value.expected2 as E
                }
            }

            return if (value.index1 == index) {
                value.update1 as E
            } else {
                value.update2 as E
            }
        }

        return value as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = if (index1 < index2) {
            CAS2Descriptor(
                index1 = index1, expected1 = expected1, update1 = update1,
                index2 = index2, expected2 = expected2, update2 = update2
            )
        } else {
            CAS2Descriptor(
                index1 = index2, expected1 = expected2, update1 = update2,
                index2 = index1, expected2 = expected1, update2 = update1
            )
        }
        descriptor.apply()
        return descriptor.status.get() === SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = AtomicReference(UNDECIDED)

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.

            val installed = installDescriptors()
            if (!installed) return

            val updated = updateStatus()

            if (!updated) return
            replaceStates()
        }

        fun installDescriptors(): Boolean {
            while (true) {
                val cell1 = array.get(index1)

                if (cell1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    help(index1, cell1)
                    continue
                } else if (cell1 != expected1) {
                    status.compareAndSet(UNDECIDED, FAILED)
                    return false
                } else {
                    val success1 = array.compareAndSet(index1, expected1, this)
                    if (!success1) continue
                }

                val cell2 = array.get(index2)

                if (cell2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    help(index2, cell2)
                    continue
                } else if (cell2 != expected2) {
                    status.compareAndSet(UNDECIDED, FAILED)
                    return false
                } else {
                    val success2 = array.compareAndSet(index2, expected2, this)
                    if (!success2) continue
                }

                return true
            }
        }

        fun updateStatus(): Boolean {
            return status.compareAndSet(UNDECIDED, SUCCESS)
        }

        fun replaceStates() {
            array.compareAndSet(index1, this, update1)
            array.compareAndSet(index2, this, update2)
        }

        fun help(index: Int, other: AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            val status = other.status.get()

            when (status) {
                UNDECIDED -> {
                    if (index == other.index2) { // No need to help with installing
                        if (other.status.compareAndSet(UNDECIDED, SUCCESS)) {
                            help(index, other)
                        }
                    }

                    if (array.compareAndSet(other.index2, other.expected2, other)) {
                        help(other.index2, other)
                    } else {
                        val cell2 = array.get(other.index2)
                        if (cell2 === other) {
                            help(other.index2, other)
                        }
                    }
                }

                SUCCESS -> {
                    array.compareAndSet(other.index1, other, other.update1)
                    array.compareAndSet(other.index2, other, other.update2)
                }

                FAILED -> {
                    array.compareAndSet(other.index1, other, other.expected1)
                }
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}