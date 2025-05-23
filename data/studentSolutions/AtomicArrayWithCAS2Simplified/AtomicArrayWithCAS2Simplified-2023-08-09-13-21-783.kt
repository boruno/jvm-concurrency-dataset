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
        val descriptor = if (index1 < index2) {
            CAS2Descriptor(
                index1 = index1, expected1 = expected1, update1 = update1,
                index2 = index2, expected2 = expected2, update2 = update2
            )
        } else {
            CAS2Descriptor(
                index1 = index2, expected1 = expected1, update1 = update1,
                index2 = index1, expected2 = expected2, update2 = update2
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

        fun toDescriptor(e: Any?): CAS2Descriptor? {
            return when(e) {
                is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> this
                else -> null
            }
        }

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            if (array.compareAndSet(index1, this, this) || array.compareAndSet(index1, expected1, this)) {
                val desc = toDescriptor(array.get(index1))
                if (desc!!.status.get() == FAILED) {
                    desc.changeToExpected()
                } else if (desc!!.status.get() == SUCCESS) {
                    desc.changeToUpdate()
                }
                if (array.compareAndSet(index2, this, this) || array.compareAndSet(index2, expected2, this)) {
                    // current descriptor is already installed or new is installed
                    val desc = toDescriptor(array.get(index2))
                    if (desc!!.status.get() == FAILED) {
                        desc.changeToExpected()
                        return
                    }
                    if (desc!!.status.get() == SUCCESS) {
                        desc.changeToUpdate()
                        return
                    }
                    changeToSuccessState()
                    return
                } else {
                    // if not expected value
                    val foreignDesc = toDescriptor(array.get(index1))
                    processForeignDescriptor(foreignDesc)
                }

            } else {
                // if not expected value
                val foreignDesc = toDescriptor(array.get(index1))
                processForeignDescriptor(foreignDesc)
            }
        }

        private fun processForeignDescriptor(desc: CAS2Descriptor?) {
            if (desc != null) {
                desc.apply()
                this.apply()
            } else {
                this.changeToFailedState()
            }
        }


        private fun changeToUpdate() {
            array.compareAndSet(index1, this, update1)
            array.compareAndSet(index2, this, update2)
        }

        private fun changeToExpected() {
            array.compareAndSet(index1, this, expected1)
            array.compareAndSet(index2, this, expected2)
        }

        private fun changeToSuccessState() {
            array.compareAndSet(index1, this, update1)
            array.compareAndSet(index2, this, update2)
            this.status.compareAndSet(UNDECIDED, SUCCESS)
        }

        private fun changeToFailedState() {
            array.compareAndSet(index1, this, expected1)
            array.compareAndSet(index2, this, expected2)
            status.compareAndSet(UNDECIDED, FAILED)
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}