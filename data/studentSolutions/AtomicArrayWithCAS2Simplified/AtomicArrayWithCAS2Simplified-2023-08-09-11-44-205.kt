@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import AtomicArrayWithCAS2Simplified.Status.FAILED
import AtomicArrayWithCAS2Simplified.Status.SUCCESS
import AtomicArrayWithCAS2Simplified.Status.UNDECIDED
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray


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
        val e = array[index] as E
        return when (e) {
            is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
                when(e.status.get()) {
                    UNDECIDED, FAILED -> {
                        e.changeToFailedState()
                        array[index] as E
//                        if (e.index1 == index) e.expected1 else e.expected2
                    }
                    SUCCESS -> {
                        e.changeToSuccessState()
                        array[index] as E
//                        if (e.index1 == index) e.update1 else e.update2
                    }
                    null -> throw NullPointerException()
                } as E
            }
            else -> e
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
            // TODO: In this task, only one thread can call cas2(..),
            // TODO: so cas2(..) calls cannot be executed concurrently.
            if (array.compareAndSet(index1, expected1, this)) {
                if (array.compareAndSet(index2, expected2, this)) {
                    changeToSuccessState()
                } else {
                    changeToFailedState()
                }
            } else {
                changeToFailedState()
            }
        }

        fun changeToSuccessState() {
            this.status.compareAndSet(UNDECIDED, SUCCESS)
            array.compareAndSet(index1, this, update1)
            array.compareAndSet(index2, this, update2)
        }

        fun changeToFailedState() {
            status.compareAndSet(UNDECIDED, FAILED)
            array.compareAndSet(index1, this, expected1)
            array.compareAndSet(index2, this, expected2)
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}