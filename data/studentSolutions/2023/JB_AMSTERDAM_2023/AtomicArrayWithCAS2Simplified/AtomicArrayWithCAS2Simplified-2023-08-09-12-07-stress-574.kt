@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

package day3

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
        val element = array[index]
        if (element is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            if (element.status.get() == AtomicArrayWithCAS2SingleWriter.Status.SUCCESS) {
                return if (element.index1 == index) element.update1 as E else element.update2 as E
            }
            return if (element.index1 == index) element.expected1 as E else element.expected2 as E
        }
        return element as E
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

        private fun applyLogically(): Boolean {
            if (array.compareAndSet(index1, expected1, this)) {
                return array.compareAndSet(index2, expected2, this)
            }
            val foreignDescriptor =
                array.get(index1) as? AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor
                    ?: array.get(index1) as? AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor
            foreignDescriptor?.applyLogically()
            return false
        }

        private fun applyPhysically() {
            array.compareAndSet(index1, this, update1)
            array.compareAndSet(index2, this, update2)
        }

        fun apply() {
            if (applyLogically()) {
                status.compareAndSet(UNDECIDED, SUCCESS)
                applyPhysically()
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
                array.compareAndSet(index1, this, expected1)
                array.compareAndSet(index2, this, expected2)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}