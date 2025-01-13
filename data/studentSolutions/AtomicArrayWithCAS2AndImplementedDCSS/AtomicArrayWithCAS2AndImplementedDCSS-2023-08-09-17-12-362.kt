@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import AtomicArrayWithCAS2AndImplementedDCSS.Status.*
import java.util.concurrent.atomic.*

// This implementation never stores `null` values.
class AtomicArrayWithCAS2AndImplementedDCSS<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    fun get(index: Int): E {
        val v = array[index]
        when (v) {
            is AtomicArrayWithCAS2AndImplementedDCSS<*>.CAS2Descriptor -> {
                if (v.status.get() == SUCCESS) {
                    val result = if (v.index1 == index) {
                        v.update1 as E
                    } else {
                        v.update2 as E
                    }
                    // help?
                    return result
                } else {
                    return if (v.index1 == index) {
                        v.expected1 as E
                    } else {
                        v.expected2 as E
                    }
                }
            }

            else -> return v as E
        }
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
            val installed = installTo(index1, expected1) && installTo(index2, expected2)
            finishLogically(installed)
            finishPhysically()
        }

        private fun installTo(index: Int, expected: Any): Boolean {
            if (status.get() != UNDECIDED) {
                return false
            }
            while (true) {
                val v = array[index]
                if (v === this) {
                    // someone helped us
                    return true
                }
                else if (v === expected) {
                    dcss(index, expected, this, status, UNDECIDED)
                    return status.get() == UNDECIDED
                }
                else if (v is AtomicArrayWithCAS2AndImplementedDCSS<*>.CAS2Descriptor) {
                    // foreign descriptor, help it, and repeat
                    v.apply()
                    continue
                }
                else {
                    // unexpected value, fail and exit
                    return false
                }
            }
        }

        fun finishLogically(success: Boolean) {
            status.compareAndSet(UNDECIDED, if (success) SUCCESS else FAILED)
        }

        private fun finishPhysically() {
            if (status.get() == SUCCESS) {
                updatePhysically()
            } else {
                rollbackPhysically()
            }
        }

        private fun updatePhysically() {
            array.compareAndSet(index1, this, update1)
            array.compareAndSet(index2, this, update2)
        }

        fun rollbackPhysically() {
            array.compareAndSet(index1, this, expected1)
            array.compareAndSet(index2, this, expected2)
        }

    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }

    // TODO: Please use this DCSS implementation to ensure that
    // TODO: the status is `UNDECIDED` when installing the descriptor.
    fun dcss(
        index: Int,
        expectedCellState: Any?,
        updateCellState: Any?,
        statusReference: AtomicReference<*>,
        expectedStatus: Any?
    ): Boolean =
        if (array[index] == expectedCellState && statusReference.get() == expectedStatus) {
            array[index] = updateCellState
            true
        } else {
            false
        }
}