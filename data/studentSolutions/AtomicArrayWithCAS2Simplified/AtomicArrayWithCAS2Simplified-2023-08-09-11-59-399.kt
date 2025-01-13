@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import AtomicArrayWithCAS2Simplified.Status.*
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
            is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor -> {
                when (e.status.get()) {
                    AtomicArrayWithCAS2SingleWriter.Status.UNDECIDED, AtomicArrayWithCAS2SingleWriter.Status.FAILED -> if (index == e.index1) e.expected1 else e.expected2
                    AtomicArrayWithCAS2SingleWriter.Status.SUCCESS -> if (index == e.index1) e.update1 else e.update2
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
            val installed = install()
            updateStatus(installed)
            uninstall(installed)

        }

        private fun updateStatus(succeeded: Boolean) {
            status.set(if (succeeded) SUCCESS else FAILED)
        }

        private fun uninstall(succeeded: Boolean) {
            array.compareAndSet(index1, this, if (succeeded) update1 else expected1)
            array.compareAndSet(index2, this, if (succeeded) update2 else expected2)
        }

        private fun install(): Boolean {
            if (!array.compareAndSet(index1, expected1, this)) {
                return false
            }
            if (!array.compareAndSet(index2, expected2, this)) {
                return false
            }
            return true
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}