@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import AtomicArrayWithCAS2SingleWriter.Status.*
import java.util.concurrent.atomic.*

// This implementation never stores `null` values.
class AtomicArrayWithCAS2SingleWriter<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    fun get(index: Int): E {
        // TODO: the cell can store CAS2Descriptor
        val v = array[index]
        if (v is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor)
            return when (v.status.get()!!){ //todo(vg): how should we decide if the first or the second value in the descriptor is ours one???
                UNDECIDED, FAILED -> v.expected1 as E
                SUCCESS -> v.update1 as E
            }
        return v as E
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
        val index1: Int, val expected1: E, val update1: E,
        val index2: Int, val expected2: E, val update2: E
    ) {
        val status = AtomicReference(UNDECIDED)

        private fun installDescriptor(): Boolean {
            if (array.compareAndSet(index1, expected1, this))
                if (array.compareAndSet(index2, expected2, this)) {
                    status.set(SUCCESS)
                    return true //note(vg): we have installed the descriptor successfully
                } else {
                    //note(vg): we have failed to install the descriptor, need to roll back the change in the cell #1
                    status.set(FAILED)
                    array.set(index1, expected1)
                }
            return false
        }

        private fun updateStatus() {
//            status.set(SUCCESS)
        }

        private fun updateCells() {
            array.set(index1, update1)
            array.set(index2, update2)
        }

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            // TODO: In this task, only one thread can call cas2(..),
            // TODO: so cas2(..) calls cannot be executed concurrently.
            if (installDescriptor()) {
                updateStatus()
                updateCells()
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}