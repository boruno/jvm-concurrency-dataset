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

    fun get(index: Int): Any? {
        // TODO: the cell can store CAS2Descriptor
//        return array[index]
        val state = array[index]
        if (state is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            if (index == state.index1) {
                return if (state.status.get() != FAILED) state.update1 else state.expected1
            }
            return if (state.status.get() != FAILED) state.update2 else state.expected2
        } else {
            return state
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
        val index1: Int, val expected1: E, val update1: E,
        val index2: Int, val expected2: E, val update2: E
    ) {
        val status = AtomicReference(UNDECIDED)

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            // TODO: In this task, only one thread can call cas2(..),
            // TODO: so cas2(..) calls cannot be executed concurrently.
            if (!array.compareAndSet(index1, expected1, this)) {
                status.set(FAILED)
                return
            }
            if (!array.compareAndSet(index2, expected2, this)) {
                status.set(FAILED)
                array.set(index1, expected1)
                return
            }
            status.set(SUCCESS)
            array.set(index1, update1)
            array.set(index2, update2)
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}