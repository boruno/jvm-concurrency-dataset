@file:Suppress("DuplicatedCode")

//package day4

import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class AtomicArrayWithCAS2<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<E>(size)
    private val descriptors = atomicArrayOfNulls<Descriptor<E>>(size) // Array for storing descriptors

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E? {
        // Check for descriptor
        val descriptor = descriptors[index].value
        if (descriptor != null) {
            helpComplete(descriptor)
        }
        return array[index].value
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        // Check for descriptor
        val descriptor = descriptors[index].value
        if (descriptor != null) {
            helpComplete(descriptor)
        }
        return array[index].compareAndSet(expected, update)
    }

    fun cas2(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?, update2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = Descriptor(index1, expected1, update1, index2, expected2, update2)
        if (!descriptors[index1].compareAndSet(null, descriptor)) return false
        helpComplete(descriptor)
        return array[index2].value == update2
    }

    private fun helpComplete(descriptor: Descriptor<E>) {
        if (descriptors[descriptor.index1].value == descriptor) {  // Check if the descriptor is still pending
            if (array[descriptor.index2].compareAndSet(descriptor.expected2, descriptor.update2)) {
                array[descriptor.index1].compareAndSet(descriptor.expected1, descriptor.update1)
            } else {
                descriptors[descriptor.index1].compareAndSet(descriptor, null)
            }
        }
    }

    // A descriptor for cas2 operations
    private class Descriptor<E>(
        val index1: Int,
        val expected1: E?,
        val update1: E?,
        val index2: Int,
        val expected2: E?,
        val update2: E?
    )
}
