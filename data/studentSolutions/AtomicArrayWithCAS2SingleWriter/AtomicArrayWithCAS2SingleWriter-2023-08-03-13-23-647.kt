//package day3

import AtomicArrayWithCAS2SingleWriter.Status.*
import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class AtomicArrayWithCAS2SingleWriter<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E {
        // TODO: the cell can store CAS2Descriptor
        val value = array[index].value
        if (value is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            return value.getInnerValue(index) as E
        } else {
            return value as E
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
        return descriptor.status.value === SUCCESS
    }

    inner class CAS2Descriptor(
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E,
        private val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            assert(index1 != index2)
            if (installDescriptors()) {
                if (applyLogically())
                    applyPhysically()
                else
                    status.compareAndSet(
                        UNDECIDED,
                        FAILED
                    )
            } else
                status.compareAndSet(
                    UNDECIDED,
                    FAILED
                )
        }

        private fun applyPhysically() {
            array[index1].compareAndSet(this, update1)
            array[index2].compareAndSet(this, update2)
        }

        private fun applyLogically(): Boolean {
            return status.compareAndSet(
                UNDECIDED,
                SUCCESS
            )
        }

        private fun installDescriptors(): Boolean {
            var smallerIndex = index1
            var biggerIndex = index2
            var smallerExpected = expected1
            var biggerExpected = expected2
            if (index1 > index2) {
                smallerIndex = index2
                biggerIndex = index1
                smallerExpected = expected2
                biggerExpected = expected1
            }
            if (array[smallerIndex].compareAndSet(smallerExpected, this)) {
                if (array[biggerIndex].compareAndSet(biggerExpected, this)) {
                    return true
                }
            }
            return false
        }

        fun getInnerValue(index: Int): E {
            if (status.value == SUCCESS) {
                return if (index == index1) update1 else update2
            } else {
                return if (index == index1) expected1 else expected2
            }
        }
    }


    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}