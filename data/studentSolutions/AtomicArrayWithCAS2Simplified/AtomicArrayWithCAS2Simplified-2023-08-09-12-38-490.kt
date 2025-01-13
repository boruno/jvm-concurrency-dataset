@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import AtomicArrayWithCAS2Simplified.Status.*
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
        val v = array[index]
        when (v) {
            is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
                if (v.status.get() == SUCCESS) {
                    val result = if (v.index1 == index) {
                        v.update1 as E
                    } else {
                        v.update2 as E
                    }
                    // help?
                    return result
                } else {
                    return if (v.index1 == index) {
                        v.expected1 as E
                    } else {
                        v.expected2 as E
                    }
                }
            }
            else -> return v as E
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

            val idx = if (index1 < index2) listOf(index1, index2) else listOf(index2, index1)
            val exp = if (index1 < index2) listOf(expected1, expected2) else listOf(expected2, expected1)
            if (installTo(idx[0], exp[0]) && installTo(idx[1], exp[1])) {
                successLogically()
            } else {
                failLogically()
            }

            finish()
        }

        private fun installTo(index: Int, expected: Any): Boolean {
            while (true) {
                if (array.compareAndSet(index, expected, this)) {
                    return true
                }
                val v = array.get(index)
                if (v !is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    // unexpected value, fail and exit
                    failLogically()
                    return false
                }
                if (v == this) {
                    // someone helped us
                    return true
                }
                if (v.status.get() == SUCCESS) {
                    // foreign descriptor, help it finish and repeat
                    v.finish()
                    continue
                }
                if (v.status.get() == FAILED) {
                    // foreign descriptor, help it finish and repeat
                    v.finish()
                    continue
                }

                // foreign which is not fully installed, help and repeat
                v.apply()
//                // foreign which is not fully installed, help and repeat
//                val otherIdx = if (v.index1 == index1) v.index2 else v.index1
//                val otherExpected = if (v.index1 == index1) v.expected2 else v.expected1
//                v.installTo(otherIdx, otherExpected)
            }
        }

        private fun finish() {
            if (status.get() == SUCCESS) {
                updatePhysically()
            } else {
                rollbackPhysically()
            }
        }

        fun successLogically(): Boolean{
            return status.compareAndSet(UNDECIDED, SUCCESS)
        }

        private fun failLogically(): Boolean {
            return status.compareAndSet(UNDECIDED, FAILED)
        }

        private fun updatePhysically() {
            array.compareAndSet(index1, this, update1)
            array.compareAndSet(index2, this, update2)
        }

        fun rollbackPhysically() {
            array.compareAndSet(index1, this, expected1)
            array.compareAndSet(index2, this, expected2)
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}