@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

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
        val state = array[index]
        if (state is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            if (state.status.get() == SUCCESS) {
                if (index == state.index1) {
                    return state.update1 as E
                }
                return state.update2 as E
            } else {
                if (index == state.index1) {
                    return state.expected1 as E
                }
                return state.expected2 as E
            }
        } else {
            return state as E
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = CAS2Descriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2, update2 = update2
        )
        descriptor.apply()
        return descriptor.status.get() === SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        private val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = AtomicReference(UNDECIDED)

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            if (status.get() == UNDECIDED) {
                install()
                updateStatus()
            }
            updateCells()
        }

        fun install() {
            if (index1 < index2) {
                while (true) {
                    if (!array.compareAndSet(index1, expected1, this)) {
                        if (help(array.get(index1))) {
                            continue
                        } else {
                            break
                        }
                    }
                }
                while (true) {
                    if (!array.compareAndSet(index2, expected2, this)) {
                        if (help(array.get(index2))) {
                            continue
                        } else {
                            break
                        }
                    }
                }
            } else {
                while (true) {
                    if (!array.compareAndSet(index2, expected2, this)) {
                        if (help(array.get(index2))) {
                            continue
                        } else {
                            break
                        }
                    }
                }
                while (true) {
                    if (!array.compareAndSet(index1, expected1, this)) {
                        if (help(array.get(index1))) {
                            continue
                        } else {
                            break
                        }
                    }
                }
            }
        }

        fun help(foreign: Any?): Boolean {
            if (foreign is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor && foreign != this) {
                foreign.apply()
                return true
            }
            return false
        }

        fun updateStatus() {
            if (array.get(index1) == this && array.get(index2) == this) {
                status.compareAndSet(UNDECIDED, SUCCESS)
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }
        }

        fun updateCells() {
            if (status.get() == SUCCESS) {
                array.compareAndSet(index1, this, update1)
                array.compareAndSet(index2, this, update2)
            } else {
                array.compareAndSet(index1, this, expected1)
                array.compareAndSet(index2, this, expected2)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}