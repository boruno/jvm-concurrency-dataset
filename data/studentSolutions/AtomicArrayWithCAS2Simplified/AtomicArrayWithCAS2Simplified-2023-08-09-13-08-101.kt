@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
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
        val result = array[index]
        if (result is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            return if (result.status.get() == AtomicArrayWithCAS2SingleWriter.Status.SUCCESS)
                result.getUpdatedByIndex(index) as E
            else
                result.getExpectedByIndex(index) as E
        }
        return result as E
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

            val descriptorsInstalled = installDescriptors()
            updateStatus(descriptorsInstalled)
            updateCells()
        }

        private fun installDescriptors(): Boolean {

            while (true) {
                val (iFirst, eFirst) = if (index1 < index2) index1 to expected1 else index2 to expected2
                val (iSecond, eSecond) = if (index1 < index2) index2 to expected2 else index1 to expected1

                return installDescriptor(iFirst, eFirst) && installDescriptor(iSecond, eSecond)

//                val r1 = array.get(iFirst)
//                if (r1 != this) {
//                    if (r1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
//                        r1.apply()
//                        continue
//                    } else {
//                        return false
//                    }
//                }


//                val r1 = array.get(iFirst)
//                if (r1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
//                    r1.apply()
//                    continue
//                } else {
//
//                }


//                val r1 = array.compareAndExchange(iFirst, eFirst, this)
//                if (r1 != eFirst && r1 != this) {
//                    if (r1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
//                        r1.apply()
//                        continue
//                    } else {
//                        return false
//                    }
//                }
//
//                return array.compareAndExchange(iSecond, eSecond, this) == eSecond
//                return r2 == eSecond || r2 == this
            }
        }

        private fun installDescriptor(index: Int, expected: E): Boolean {
            while (true) {
                val current = array.get(index)
                if (current == this) return true
                if (current is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    if (current.status.get() == UNDECIDED) {
                        current.apply()
                    } else {
                        current.updateCells()
                    }
                    continue
                }
                return array.compareAndSet(index, expected, this)
            }
        }

        private fun updateStatus(descriptorsInstalled: Boolean) {
            if (descriptorsInstalled)
                status.compareAndSet(UNDECIDED, SUCCESS)
            else
                status.compareAndSet(UNDECIDED, FAILED)
        }

        private fun updateCells() {
            if (status.get() == SUCCESS) {
                array.compareAndSet(index1, this, update1)
                array.compareAndSet(index2, this, update2)
            } else {
                array.compareAndSet(index1, this, expected1)
                array.compareAndSet(index2, this, expected2)
            }
        }

    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}