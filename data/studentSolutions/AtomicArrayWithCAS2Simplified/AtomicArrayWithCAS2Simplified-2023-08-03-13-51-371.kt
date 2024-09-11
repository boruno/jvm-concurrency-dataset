package day3

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

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        val currentValue = array[index].value
        return if (currentValue is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            val isFirstValue = index == currentValue.index1
            when (currentValue.status.value) {
                UNDECIDED, FAILED -> if (isFirstValue) currentValue.expected1 else currentValue.expected2
                SUCCESS -> if (isFirstValue) currentValue.update1 else currentValue.update2
            }
        } else {
            currentValue
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
        return descriptor.status.value === SUCCESS
    }

    private inner class CAS2Descriptor(
//        val index1: Int,
//        val expected1: E,
//        val update1: E,
//        val index2: Int,
//        val expected2: E,
//        val update2: E

        index1: Int,
        expected1: E,
        update1: E,
        index2: Int,
        expected2: E,
        update2: E
    ) {

        val index1: Int
        val expected1: E
        val update1: E
        val index2: Int
        val expected2: E
        val update2: E

        init {
            if (index1 < index2) {
                this.index1 = index1
                this.index2 = index2
                this.expected1 = expected1
                this.expected2 = expected2
                this.update1 = update1
                this.update2 = update2
            } else {
                this.index1 = index2
                this.index2 = index1
                this.expected1 = expected2
                this.expected2 = expected1
                this.update1 = update2
                this.update2 = update1
            }
        }

        val status = atomic(UNDECIDED)

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            // TODO: In this task, only one thread can call cas2(..),
            // TODO: so cas2(..) calls cannot be executed concurrently.
            installDescriptor()
            updateLogically()
            updatePhysically()
        }

        private fun installDescriptor() {
            if (status.value !== UNDECIDED) return
            while (true) {
                val currentValue1 = array[index1].value

                if (currentValue1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    if (currentValue1 != this) {
                        currentValue1.apply()
                    } else {
                        return
                    }
                } else {
                    if (array[index1].compareAndSet(expected1, this)) {
                        break
                    }
//                    else {
//                        if (array[index1].value !is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
//                            return
//                        }
//                    }
                }
            }
            while (true) {
                val currentValue2 = array[index2].value
                if (currentValue2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    if (currentValue2 !== this) {
                        currentValue2.apply()
                    } else {
                        break
                    }
                } else {
                    if (array[index2].compareAndSet(expected2, this)) {
                        break
                    }
//                    else {
//                        if (array[index2].value !is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
//                            break
//                        }
//                    }
//
//
//                    array[index2].compareAndSet(expected2, this)
//                    break
                }
            }
        }

        private fun updateLogically() {
            val newStatus = if (array[index1].value == this && array[index2].value == this) SUCCESS else FAILED
            status.compareAndSet(UNDECIDED, newStatus)
        }

        private fun updatePhysically() {
            when (status.value) {
                SUCCESS -> {
//                    while (true) {
//                        val currentValue = array[index1].value
//                        if (currentValue is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor && currentValue !== this) {
//                            currentValue.apply()
//                        } else {
//
//                            break
//                        }
//                    }
                    array[index1].compareAndSet(this, update1)
                    array[index2].compareAndSet(this, update2)
                }
                FAILED -> {
//                    while (true) {
//                        val currentValue = array[index1].value
//                        if (currentValue is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor && currentValue !== this) {
//                            currentValue.apply()
//                        } else {
//                            array[index1].compareAndSet(this, expected1)
//                            break
//                        }
//                    }
//
//                    while (true) {
//                        val currentValue = array[index2].value
//                        if (currentValue is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor && currentValue !== this) {
//                            currentValue.apply()
//                        } else {
//                            array[index2].compareAndSet(this, expected2)
//                            break
//                        }
//                    }

                    array[index1].compareAndSet(this, expected1)
                    array[index2].compareAndSet(this, expected2)
                }
                UNDECIDED -> error("Unexpected")
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}