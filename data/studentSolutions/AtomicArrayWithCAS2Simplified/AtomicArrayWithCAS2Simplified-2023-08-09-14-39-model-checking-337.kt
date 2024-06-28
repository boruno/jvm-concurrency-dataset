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
        if (element is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            if (element.status.get() == SUCCESS) {
                return element.getUpdate(index) as E
            }
            return element.getExpected(index) as E
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

        private fun applyLogically(): Boolean {
            if (status.get() != UNDECIDED) return false
            if (!install(index1)) return false
            return install(index2)
        }

        private fun install(index: Int): Boolean {
            while (true) {
                val state = array.get(index)
                if (state is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    if (state == this) return true
                    state.apply()
                    continue
                }
                return array.compareAndSet(index, getExpected(index), this)
            }
        }

        fun getExpected(index: Int) = if (index == index1) expected1 else expected2

        fun getUpdate(index: Int) = if (index == index1) update1 else update2

        private fun applyPhysically() {
            if (status.get() == SUCCESS) {
                array.compareAndSet(index1, this, update1)
                array.compareAndSet(index2, this, update2)
            }
            if (status.get() == FAILED) {
                array.compareAndSet(index1, this, expected1)
                array.compareAndSet(index2, this, expected2)
            }
        }

        private fun updateStatus(success: Boolean) {
            status.compareAndSet(UNDECIDED, if (success) SUCCESS else FAILED)
        }

        fun apply() {
            val success = applyLogically()
            updateStatus(success)
            applyPhysically()
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}