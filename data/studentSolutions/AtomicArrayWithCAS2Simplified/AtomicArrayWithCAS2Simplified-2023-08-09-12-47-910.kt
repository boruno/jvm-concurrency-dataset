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
        val node = array[index]
        return if (node is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            node.getValue(index) as E
        } else {
            node as E
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

            val statusSnapshot = status.get()
            if (statusSnapshot == UNDECIDED) {
                val result = tryInstallDescriptor()
                applyLogicalStatusUpdate(result)
                applyPhysicalStatusUpdate(result)
            } else {
                val isSuccess = statusSnapshot == SUCCESS
                applyLogicalStatusUpdate(isSuccess)
            }
        }

        private fun tryInstallDescriptor(): Boolean {
            val (indexA, indexB) = if (index1 >= index2) {
                index2 to index1
            } else {
                index1 to index2
            }
            tryHelpDescriptor(array[indexA])
            tryHelpDescriptor(array[indexB])

            return array.compareAndSet(index1, expected1, this)
                    && array.compareAndSet(index2, expected2, this)
        }

        private fun tryHelpDescriptor(node: Any?) {
            val nodeA = node
            if (nodeA is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                nodeA.apply()
            }
        }

        private fun applyLogicalStatusUpdate(result: Boolean) {
            val newStatus = if (result) Status.SUCCESS else Status.FAILED
            status.compareAndSet(Status.UNDECIDED, newStatus)
        }

        private fun applyPhysicalStatusUpdate(result: Boolean) {
            val (value1, value2) = if (result) {
                update1 to update2
            } else {
                expected1 to expected2
            }

            array.compareAndSet(index1, this, value1)
            array.compareAndSet(index2, this, value2)
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}