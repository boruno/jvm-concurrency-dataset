@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

package day3

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
        while (true) {
            return array[index] as? E ?: continue
        }
        /*
        val state = array[index]
        if (state is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            val isA = index == state.index1
            val status: Status = state.status.get()
            return when (status) {
                Status.SUCCESS -> {
                    if (isA) state.update1
                    else state.update2
                }

                else -> {
                    if (isA) state.expected1
                    else state.expected2
                }
            } as E
        }
        return state as E
        */
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
        index1: Int,
        private val expected1: E,
        private val update1: E,
        index2: Int,
        private val expected2: E,
        private val update2: E
    ) {
        private val _index1 = min(index1, index2)
        private val _index2 = max(index1, index2)

        val status = AtomicReference(Status.UNDECIDED)

        fun apply() {
            installDescriptors()
            if (status.get() == Status.UNDECIDED) updateLogically()
            updatePhysically()
        }

        private fun installDescriptors() {
            tryInstallDescriptor(_index1, expected1)
            tryInstallDescriptor(_index2, expected2)
        }

        private fun tryInstallDescriptor(index: Int, expected: E) {
            while (true) {
                val state = array[index]
                if (state !is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    array.compareAndSet(index, expected, this)
                    return
                }
                if (state === this) return

                // helping the descriptor
                val otherIndex = if (index == state._index1) state._index2 else state._index1
                val otherState = array[otherIndex]
                if (otherState === state) {
                    state.status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
                }
            }
        }

        private fun updateLogically() {
            if (array[_index1] === this && array[_index2] === this) {
                status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
            } else {
                status.compareAndSet(Status.UNDECIDED, Status.FAILED)
            }
        }

        private fun updatePhysically() {
            val result1 = if (status.get() == Status.SUCCESS) update1 else expected1
            val result2 = if (status.get() == Status.SUCCESS) update2 else expected2

            array.compareAndSet(_index1, this, result1)
            array.compareAndSet(_index2, this, result2)
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}