//package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.*


// This implementation never stores `null` values.
class AtomicArrayWithCAS2Simplified<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E {
        val value = array[index].value
        if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            if (value.status.value === SUCCESS) {
                return if (value.index1 == index) value.update1 as E else value.update2 as E
            }
            else {
                return if (value.index1 == index) value.expected1 as E else value.expected2 as E
            }
        }
        return value as E
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
        return descriptor.status.value === SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun friendlyApply(index: Int) {
            if (index == index1) {
                if (!array[index2].compareAndSet(expected2, this)) {
                    if (array[index2].value != this) {
                        status.compareAndSet(UNDECIDED, FAILED)
                        array[index1].compareAndSet(this, expected1)
                    }
                    else {
                        status.compareAndSet(UNDECIDED, SUCCESS)
                        array[index1].compareAndSet(this, update1)
                        array[index2].compareAndSet(this, update2)
                    }
                    return
                }
                status.compareAndSet(UNDECIDED, SUCCESS)
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            }
            else {
                if (array[index1].compareAndSet(expected1, this)) {
                    if (array[index1].value != this) {
                        status.compareAndSet(UNDECIDED, FAILED)
                        array[index2].compareAndSet(this, expected2)
                    }
                    else {
                        status.compareAndSet(UNDECIDED, SUCCESS)
                        array[index1].compareAndSet(this, update1)
                        array[index2].compareAndSet(this, update2)
                    }
                    return
                }
                status.compareAndSet(UNDECIDED, SUCCESS)
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            }

        }

        fun apply() {
            val (idx1, idx2) = if (index1 >= index2) (index2 to index1) else (index1 to index2)
            val (exp1, exp2) = if (index1 >= index2) (expected2 to expected1) else (expected1 to expected2)

            when (val desc = array[idx1].value) {
                is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> if (desc != this) desc.friendlyApply(idx1)
            }
            when (val desc = array[idx2].value) {
                is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> if (desc != this) desc.friendlyApply(idx2)
            }

            if (!array[idx1].compareAndSet(exp1, this)) {
                return
            }
            if (!array[idx2].compareAndSet(exp2, this)) {
                status.compareAndSet(UNDECIDED, FAILED)
                array[idx1].compareAndSet(this, exp1)
                return
            }
            else {
                status.compareAndSet(UNDECIDED, SUCCESS)
            }

            array[index1].compareAndSet(this, update1)
            array[index2].compareAndSet(this, update2)

            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            // TODO: In this task, only one thread can call cas2(..),
            // TODO: so cas2(..) calls cannot be executed concurrently.
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}
