@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import AtomicArrayWithCAS2Simplified.Status.*
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

        private fun applyLogically() {
            if (status.get() == SUCCESS || status.get() == FAILED) return
            val minIndex: Int
            val minIndExpected: E
            val maxIndex: Int
            val maxIndexExpected: E
            if (index1 < index2) {
                minIndex = index1
                minIndExpected = expected1
                maxIndex = index2
                maxIndexExpected = expected2
            } else {
                minIndex = index2
                minIndExpected = expected2
                maxIndex = index1
                maxIndexExpected = expected1
            }
            if (!array.compareAndSet(minIndex, minIndExpected, this)) {
                val desc = array.get(minIndex) as? AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor
                if (desc != null && desc != this) desc.apply()
            }
            if (!array.compareAndSet(maxIndex, maxIndexExpected, this)) {
                val desc = array.get(maxIndex) as? AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor
                if (desc != null && desc != this) desc.apply()
            }
            if (bothEq(minIndex, maxIndex, this)) {
                status.compareAndSet(UNDECIDED, SUCCESS)
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }
        }

        fun bothEq(index1: Int, index2: Int, descriptor: CAS2Descriptor): Boolean {
            val firstVal = array.get(index1)
            val secondVal = array.get(index2)
            return firstVal === array.get(index1) && firstVal === descriptor && secondVal === descriptor
        }

        private fun applyPhysically() {
            val minIndex: Int
            val minIndUpdate: E
            val minIndExpected: E
            val maxIndex: Int
            val maxIndUpdate: E
            val maxIndExpected: E
            if (index1 < index2) {
                minIndex = index1
                minIndUpdate = update1
                minIndExpected = expected1
                maxIndex = index2
                maxIndUpdate = update2
                maxIndExpected = expected2
            } else {
                minIndex = index2
                minIndUpdate = update2
                minIndExpected = expected2
                maxIndex = index1
                maxIndUpdate = update1
                maxIndExpected = expected1
            }
            if (status.get() == SUCCESS) {
                if (!array.compareAndSet(minIndex, this, minIndUpdate)) {
                    val desc = array.get(maxIndex) as? AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor
                    if (desc != null && desc != this) desc.apply()
                }
                if (!array.compareAndSet(maxIndex, this, maxIndUpdate)) {
                    val desc = array.get(maxIndex) as? AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor
                    if (desc != null && desc != this) desc.apply()
                }
            }
            if (status.get() == FAILED) {
                if (!array.compareAndSet(minIndex, this, minIndExpected)) {
                    val desc = array.get(maxIndex) as? AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor
                    if (desc != null && desc != this) desc.apply()
                }
                if (!array.compareAndSet(maxIndex, this, maxIndExpected)) {
                    val desc = array.get(maxIndex) as? AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor
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