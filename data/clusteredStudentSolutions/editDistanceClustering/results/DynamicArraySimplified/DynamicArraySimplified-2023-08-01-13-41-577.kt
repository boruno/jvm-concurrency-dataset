//package day4

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
            if (curSize == capacity) return false
            // Try to increment the size first
            if (size.compareAndSet(curSize, curSize + 1)) {
                // If successful, then we can safely add the element
                array[curSize].value = element
                return true
            }
            // If not successful, loop back and try again
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