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
            if (curSize == capacity) return false

            // Attempt to set the element at the current size index.
            // If the operation is successful, break the loop.
            if (array[curSize].compareAndSet(null, element)) {
                break
            }
        }

        // Increment the size by 1, ensuring the operation is atomic.
        // This operation is safe because size never decreases.
        size.incrementAndGet()

        return true
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