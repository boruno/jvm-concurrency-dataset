@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

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
        while (true) {
            return array[index] as? E ?: continue
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
        return descriptor.status.get() === Status.SUCCESS
    }

    inner class CAS2Descriptor(
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E,
        private val update2: E
    ) {
        val status = AtomicReference(Status.UNDECIDED)

        fun apply() {
            installDescriptors()
            updateLogically()
            updatePhysically()
        }

        private fun installDescriptors() {
            array.compareAndSet(index1, expected1, this)
            array.compareAndSet(index2, expected2, this)
        }

        private fun updateLogically() {
            if (array[index1] === this && array[index2] === this) {
                status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
            } else {
                status.compareAndSet(Status.UNDECIDED, Status.FAILED)
            }
        }

        private fun updatePhysically() {
            val result1 = if (status.get() == Status.SUCCESS) update1 else expected1
            val result2 = if (status.get() == Status.SUCCESS) update2 else expected2

            array.compareAndSet(index1, this, result1)
            array.compareAndSet(index2, this, result2)
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}