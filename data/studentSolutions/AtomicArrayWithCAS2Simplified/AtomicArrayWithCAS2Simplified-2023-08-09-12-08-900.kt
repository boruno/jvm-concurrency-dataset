@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import day3.AtomicArrayWithCAS2Simplified.Status.FAILED
import day3.AtomicArrayWithCAS2Simplified.Status.SUCCESS
import day3.AtomicArrayWithCAS2Simplified.Status.UNDECIDED
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
                        if (e.index1 == index) e.expected1 else e.expected2
                    }
                    SUCCESS -> {
                        if (e.index1 == index) e.update1 else e.update2
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
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = AtomicReference(UNDECIDED)


        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            val firstDescIsSet = array.get(index1) == this || array.compareAndSet(index1, expected1, this)
            if (firstDescIsSet) {
                val secDescIsSet = array.get(index2) == this || array.compareAndSet(index2, expected2, this)
                if (secDescIsSet) {
                    changeToSuccessState()
                } else {
                    changeToFailedState()
                }
            } else {
                // if not expected value
                when(array.get(index1)) {
                    // if foreign descriptor
                    is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
                        val foreignDesc = array.get(index1) as AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor
                        foreignDesc.apply()
                    }
                    else -> changeToFailedState()
                }
            }
        }

        private fun processDescriptor() {
            if (array.compareAndSet(index1, expected1, this)) {
                if (array.compareAndSet(index2, expected2, this)) {
                    changeToSuccessState()
                } else {
                    changeToFailedState()
                }
            } else {
                // if not expected value
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