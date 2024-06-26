package day4

import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class DynamicArraySimplified<E : Any>(
    private val capacity: Int
) {
    private val array = atomicArrayOfNulls<Any?>(capacity)
    private val size = atomic(0) // never decreases

    fun addLast(element: E): Boolean {
        while (true) {
            val curSize = size.value
            if (curSize == capacity) return false // The array is full, return false.

            // Try to install the element atomically using compareAndSet.
            if (array[curSize].compareAndSet(null, element)) {
                // Successfully added the element, increment the size atomically.
                size.incrementAndGet()
                return true
            }

            // Another thread modified the element at index curSize, so we retry.
        }
    }


    fun set(index: Int, element: E) {
        val curSize = size.value
        require(index < curSize) { "index must be lower than the array size" }
        // As the size never decreases, this update is safe.
        array[index].value = element
    }

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        val curSize = size.value
        require(index < curSize) { "index must be lower than the array size" }
        // As the size never decreases, this read is safe.
        return array[index].value as E
    }
}