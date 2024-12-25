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

            if (!array[curSize].compareAndSet(null, element)) {
                // Another thread has already inserted an element at this index.
                // Retry with the updated size.
                continue
            }

            if (size.compareAndSet(curSize, curSize + 1)) {
                // Successfully incremented the size. This means we were the
                // thread that inserted the last element.
                return true
            } else {
                // Another thread has already incremented the size. Our inserted
                // element has been overwritten, so we need to try again.
                // First, reset the element at the index we attempted to insert to.
                array[curSize].compareAndSet(element, null)
            }
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