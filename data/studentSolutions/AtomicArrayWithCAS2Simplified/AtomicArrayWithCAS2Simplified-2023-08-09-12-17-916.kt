@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

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
        val item = array[index]
        return if (item is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            item.value(index)
        } else {
            item
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

        fun value(index: Int): E {
            return if (status.get() == Status.SUCCESS) {
                when (index) {
                    index1 -> update1
                    index2 -> update2
                    else -> error("Unexpected index")
                }
            } else {
                when (index) {
                    index1 -> expected1
                    index2 -> expected2
                    else -> error("Unexpected index")
                }
            }
        }

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.

            if (!installDescriptor(index1, expected1)) {
                // CAS2 failed, we need to revert.
                status.set(FAILED)
                // Here we can sure that the state of the `index2` is either A
                // descriptor or the original value. `index2` may contain this | original value | another descriptor.
                // If it contains anything different from the current descriptor we just exit
                array.compareAndSet(index2, this, expected2)
                return
            }

            if (!installDescriptor(index2, expected2)) {
                status.set(FAILED)
                array.compareAndSet(index1, this, expected1)
                return
            }

            setSuccess()

            uninstallDescriptor(index1, update1)
            uninstallDescriptor(index2, update2)
        }

        private fun uninstallDescriptor(index: Int, update: E): Boolean {
            if (array.compareAndSet(index, this, update)) {
                return true
            } else {
                // Another thread might have uninstalled the current descriptor already
                // and the value in the cell might have been already changed after that
                // So, should we just ignore this?
                return true
            }
        }

        private fun setSuccess() {
            if (!status.compareAndSet(UNDECIDED, SUCCESS)) {
                // The status might be changed by a different thread.
                // At this point we are sure that the descriptor was installed to both nodes, so the target status
                // could be only SUCCESS
                val status = status.get()
                if (status != SUCCESS) {
                    error("How could be? Actual status is $status")
                }
            }
        }

        private fun installDescriptor(index: Int, expected: E): Boolean {
            while (true) {
                if (array.compareAndSet(index, expected, this)) {
                    // the cell state was as expected, and we updated the cell state to the descriptor
                    return true
                } else {
                    // either the cell value was already updated or a descriptor was installed
                    val foreignDescriptor = array[index] as? AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor
                    if (foreignDescriptor != null) {
                        // we need to help the foreign descriptor to complete
                        if (this == foreignDescriptor) {
                            // another thread already installed the current descriptor
                            return true
                        } else {
                            // that is a foreign descriptor, and we need to help it
                            foreignDescriptor.apply()
                            // and retry to install the current descriptor
                        }
                    } else {
                        return false
                    }
                }
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}