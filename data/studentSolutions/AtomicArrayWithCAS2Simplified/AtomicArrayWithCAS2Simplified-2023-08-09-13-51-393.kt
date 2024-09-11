@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import java.util.concurrent.atomic.*
import kotlin.math.max
import kotlin.math.min


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
        val descriptor: CAS2Descriptor = if (index1 < index2)
            CAS2Descriptor(
                index1 = index1, expected1 = expected1, update1 = update1,
                index2 = index2, expected2 = expected2, update2 = update2
            ) else {
            CAS2Descriptor(
                index1 = index2, expected1 = expected2, update1 = update2,
                index2 = index1, expected2 = expected1, update2 = update1
            )
        }
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

        private fun applyLogically() {
            while (true) {
                if (status.get() != UNDECIDED) return
                if (!array.compareAndSet(index1, expected1, this)) {
                    val desc = array.get(index1) as? AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor
                    if (desc != null && desc != this) {
                        desc.apply()
                        continue
                    }
                }
                if (!array.compareAndSet(index2, expected2, this)) {
                    val desc = array.get(index1) as? AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor
                    if (desc != null && desc != this) {
                        desc.apply()
                        continue
                    }
                }
                if (bothEq(index1, index2, this)) {
                    status.compareAndSet(UNDECIDED, SUCCESS)
                } else {
                    status.compareAndSet(UNDECIDED, FAILED)
                }
                return
            }
        }

        fun bothEq(index1: Int, index2: Int, descriptor: CAS2Descriptor): Boolean {
            val firstVal = array.get(index1)
            val secondVal = array.get(index2)
            return firstVal === array.get(index1) && firstVal === descriptor && secondVal === descriptor
        }

        private fun applyPhysically() {
            if (status.get() == SUCCESS) {
                if (!array.compareAndSet(index1, this, update1)) {
                    val desc = array.get(index2) as? AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor
                    if (desc != null && desc != this) desc.apply()
                }
                if (!array.compareAndSet(index2, this, update2)) {
                    val desc = array.get(index2) as? AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor
                    if (desc != null && desc != this) desc.apply()
                }
            }
            if (status.get() == FAILED) {
                if (!array.compareAndSet(index1, this, expected1)) {
                    val desc = array.get(index2) as? AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor
                    if (desc != null && desc != this) desc.apply()
                }
                if (!array.compareAndSet(index2, this, expected2)) {
                    val desc = array.get(index2) as? AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor
                    if (desc != null && desc != this) desc.apply()
                }
            }
        }

        fun apply() {
            applyLogically()
            applyPhysically()
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}