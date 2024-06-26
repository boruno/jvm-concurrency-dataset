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
        val result = array[index]
        if (result is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            return when {
                result.index1 == index -> {
                    if (result.status.get() == AtomicArrayWithCAS2SingleWriter.Status.SUCCESS) result.update1 as E
                    else result.expected1 as E
                }

                else -> {
                    if (result.status.get() == AtomicArrayWithCAS2SingleWriter.Status.SUCCESS) result.update2 as E
                    else result.expected2 as E
                }
            }
        }

        return result as E
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
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E,
        private val update2: E
    ) {
        val status = AtomicReference(UNDECIDED)

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            if (status.get() == UNDECIDED) {
                array.compareAndSet(index1, this, expected1)
                array.compareAndSet(index2, this, expected2)
            }

            if (!installDescriptor()) {
                return
            }

            logicallyUpdate()
            updateCells()
        }

        private fun installDescriptor(): Boolean {
            status.set(UNDECIDED)

            val cur1 = array[index1]
            if (!array.compareAndSet(index1, expected1, this)) {
                status.set(FAILED)
                array[index1] = cur1
                return false
            }

            val cur2 = array[index2]
            if (!array.compareAndSet(index2, expected2, this)) {
                array[index1] = cur1
                array[index2] = cur2
                status.set(FAILED)
                return false
            }

            return true
        }

        private fun logicallyUpdate() = status.set(SUCCESS)

        private fun updateCells() {
            array[index1] = update1
            array[index2] = update2
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}