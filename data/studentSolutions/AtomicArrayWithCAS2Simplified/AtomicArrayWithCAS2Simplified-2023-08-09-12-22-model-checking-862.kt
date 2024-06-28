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

        return if (value is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            when (value.status.get()!!) {
                AtomicArrayWithCAS2SingleWriter.Status.UNDECIDED, AtomicArrayWithCAS2SingleWriter.Status.FAILED -> {
                    if (value.index1 == index) value.expected1 as E
                    else value.expected2 as E
                }

                AtomicArrayWithCAS2SingleWriter.Status.SUCCESS -> {
                    if (value.index1 == index) value.update1 as E
                    else value.update2 as E
                }
            }
        } else {
            value as E
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
            val otherDescriptor = when {
                array[index1] is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor ->
                    array[index1] as AtomicArrayWithCAS2Simplified<E>.CAS2Descriptor

                array[index2] is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor ->
                    array[index2] as AtomicArrayWithCAS2Simplified<E>.CAS2Descriptor

                else -> null
            }

            if (otherDescriptor != null) {
                if (tryUpdateLogically(otherDescriptor)) {
                    otherDescriptor.status.compareAndSet(UNDECIDED, SUCCESS)
                    tryUpdatePhysically(otherDescriptor)
                } else {
                    otherDescriptor.status.compareAndSet(UNDECIDED, FAILED)
                    tryRollback(otherDescriptor)
                }
            }

            if (tryUpdateLogically(this)) {
                status.compareAndSet(UNDECIDED, SUCCESS)
                tryUpdatePhysically(this)
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
                tryRollback(this)
            }
        }

        private fun tryUpdateLogically(descriptor: CAS2Descriptor): Boolean {
            return array.compareAndSet(index1, expected1, descriptor) &&
                    array.compareAndSet(index2, expected2, descriptor)
        }

        private fun tryUpdatePhysically(descriptor: CAS2Descriptor): Boolean {
            return array.compareAndSet(index1, descriptor, update1) &&
                    array.compareAndSet(index2, descriptor, update2)
        }

        private fun tryRollback(descriptor: CAS2Descriptor): Boolean {
            return array.compareAndSet(index1, descriptor, expected1) &&
                    array.compareAndSet(index2, descriptor, expected2)
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}