@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

package day3

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
        val value = array[index]
        if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            val descriptor = value as AtomicArrayWithCAS2Simplified<E>.CAS2Descriptor
            return descriptor.getValue(index)
        }
        return value as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E,
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
        private val update2: E,
    ) {
        val status = AtomicReference(UNDECIDED)

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            val installed = installDescriptor()
            applyLogically(installed)
            applyPhysically()
        }

        fun getValue(index: Int): E {
            return when (status.get()!!) {
                UNDECIDED,FAILED -> when (index) {
                    index1 -> expected1
                    index2 -> expected2
                    else -> throw RuntimeException()
                }

                SUCCESS -> when (index) {
                    index1 -> update1
                    index2 -> update2
                    else -> throw RuntimeException()
                }
            }
        }

        private fun installDescriptor(): Boolean {
            if (array.compareAndSet(index1, expected1, this)) {
                if (array.compareAndSet(index2, expected2, this)) {
                    return true
                } else {
                    return maybeHelpingForeignDescriptor(index2)
                }
            } else {
                return maybeHelpingForeignDescriptor(index1)
            }
        }

        private fun maybeHelpingForeignDescriptor(index: Int): Boolean {
            val unexpected = array.get(index)
            if (unexpected == this) return true
            if (unexpected is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
                val unexpectedDescriptor = unexpected as AtomicArrayWithCAS2SingleWriter<E>.CAS2Descriptor
                unexpectedDescriptor.apply()
                return installDescriptor()
            } else {
                return false
            }
        }

        private fun applyLogically(installed: Boolean): Boolean {
            return when {
                installed -> status.compareAndSet(UNDECIDED, SUCCESS)
                else -> status.compareAndSet(UNDECIDED, FAILED)
            }
        }

        private fun applyPhysically() {
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