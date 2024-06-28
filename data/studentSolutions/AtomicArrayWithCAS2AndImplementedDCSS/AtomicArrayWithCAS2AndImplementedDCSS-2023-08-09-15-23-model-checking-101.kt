@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

package day3

import day3.AtomicArrayWithCAS2AndImplementedDCSS.Status.*
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray

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
        // TODO: Copy the implementation from `AtomicArrayWithCAS2Simplified`
        val result = array.get(index)
        if (result is AtomicArrayWithCAS2AndImplementedDCSS<*>.CAS2Descriptor) {
            if (result.status.get() == SUCCESS) {
                if (index == result.index1) {
                    return result.update1 as E
                } else {
                    return result.update2 as E
                }
            } else {
                if (index == result.index1) {
                    return result.expected1 as E
                } else {
                    return result.expected2 as E
                }
            }
        } else {
            return result as E
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
            val result = install()
            if (result) {
                updatePhysically()
            }
            updateStatus(result)
        }

        private fun install(): Boolean {
            return if (install(this.index1)) {
                install(this.index2)
            } else {
                false
            }
        }

        private fun install(index: Int): Boolean {
            val curr = array.get(index)
            when (curr) {
                is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor ->
                    this.apply()

                is Int ->
                    if (index == this.index1) {
                        if (dcss(index1, expected1, update1, status, UNDECIDED)) {
                            status.compareAndSet(UNDECIDED, SUCCESS)
                            return true
                        } else {
                            status.compareAndSet(UNDECIDED, FAILED)
                            return false
                        }
                    } else {
                        if (dcss(index2, expected2, update2, status, UNDECIDED)) {
                            status.compareAndSet(UNDECIDED, SUCCESS)
                            return true
                        } else {
                            status.compareAndSet(UNDECIDED, FAILED)
                            return false
                        }
                    }

                else ->
                    return false
            }
            return false
        }

        private fun updateStatus(success: Boolean) {
            if (success) this.status.compareAndSet(UNDECIDED, SUCCESS) else this.status.compareAndSet(UNDECIDED, FAILED)
        }

        private fun updatePhysically() {
            array.compareAndSet(index1, this, update1)
            array.compareAndSet(index2, this, update2)
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