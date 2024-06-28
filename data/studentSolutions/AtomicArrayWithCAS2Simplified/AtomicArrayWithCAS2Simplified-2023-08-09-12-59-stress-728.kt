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
        // TODO: the cell can store CAS2Descriptor
        return when (val e = array[index]) {
            is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
                when (e.status.get()) {
                    UNDECIDED, FAILED -> if (index == e.index1) e.expected1 else e.expected2
                    SUCCESS -> if (index == e.index1) e.update1 else e.update2
                }
            }
            else -> e
        } as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        if (index1 > index2) return cas2(index2, expected2, update2, index1, expected1, update1)
        val descriptor = CAS2Descriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2, update2 = update2
        )
        descriptor.apply()
        return descriptor.status.get() === SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int, val expected1: E, val update1: E,
        val index2: Int, val expected2: E, val update2: E
    ) {
        val status = AtomicReference(UNDECIDED)

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            install()
            uninstall()
        }

        private fun uninstall() {
            val succeeded = status.get() == SUCCESS
            while (true) {
                // happy path
                if (!array.compareAndSet(index1, this, if (succeeded) update1 else expected1)) {
                    val e = array[index1]
                    if (e is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                        if (e != this) {
                            // e.apply()
                            // continue
                        }
                    }
                }
                if (!array.compareAndSet(index2, this, if (succeeded) update2 else expected2)) {
                    val e = array[index2]
                    if (e is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                        if (e != this) {
                            e.apply()
                            continue
                        }
                    }
                }
                break
            }
        }

        private fun install() {
            while (true) {
                if (status.get() != UNDECIDED) break // already installed
                if (!array.compareAndSet(index1, expected1, this)) {
                    val value = array.get(index1)
                    if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                        if (value != this) {
                            // help
                            // value.apply()
                            // continue
                        }
                        // already installed by another thread, go on
                    } else {
                        // unexpected value
                        status.compareAndSet(UNDECIDED, FAILED)
                        break
                    }
                }
                if (!array.compareAndSet(index2, expected2, this)) {
                    val value = array.get(index2)
                    if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                        if (value != this) {
                            // help
                            value.apply()
                            continue
                        }
                        // already installed by another thread, go on
                    } else {
                        // unexpected value
                        status.compareAndSet(UNDECIDED, FAILED)
                        break
                    }
                }
                status.compareAndSet(UNDECIDED, SUCCESS)
                break
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}