//package day3

import AtomicArrayWithCAS2Simplified.Status.*
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
        // TODO: the cell can store CAS2Descriptor
        when (val element = array[index].value) {
            is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
                when (element.status.value) {
                    Status.UNDECIDED, Status.FAILED -> {
                        if (index == element.index1) {
                            return element.expected1 as E
                        } else {
                            return element.expected2 as E
                        }
                    }

                    Status.SUCCESS -> {
                        if (index == element.index1) {
                            return element.update1 as E
                        } else {
                            return element.update2 as E
                        }
                    }
                }
            }

            else -> return element as E
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = if (index1 > index2) {
            CAS2Descriptor(
                index1 = index1, expected1 = expected1, update1 = update1,
                index2 = index2, expected2 = expected2, update2 = update2
            )
        } else {
            CAS2Descriptor(
                index2 = index1, expected2 = expected1, update2 = update1,
                index1 = index2, expected1 = expected2, update1 = update2
            )
        }

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

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            val status = install()
            updateStatus(status)
            update()
        }


        private fun install(): Status {
            while (true) {
                if (array[index1].value !is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor && array[index2].value !is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    break
                }
                val descriptor = array[index1].value
                if (descriptor is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    if (descriptor.status.value == SUCCESS) break
                    if (descriptor.status.value == FAILED) break
                    if (array[descriptor.index2].compareAndSet(descriptor.expected2, descriptor)) {
                        println("we helped")
                    } else {
//                        break
                    }
//                    element1.install()
                }
            }
            if (array[index1].compareAndSet(expected1, this)
                && array[index2].compareAndSet(expected2, this)
            ) {
                return SUCCESS
            } else {
                return FAILED
            }
        }

        private fun updateStatus(newStatus: Status) {
            status.compareAndSet(UNDECIDED, newStatus)
        }

        private fun update() {
            when (status.value) {
                UNDECIDED, FAILED -> {
                    array[index1].compareAndSet(this, expected1)
                    array[index2].compareAndSet(this, expected2)
                }

                SUCCESS -> {
                    array[index1].compareAndSet(this, update1)
                    array[index2].compareAndSet(this, update2)
                }
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}