//package day4

import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class DynamicArraySimplified<E : Any>(
    private val capacity: Int
) {
    private val array = atomicArrayOfNulls<Any?>(capacity)
    private val size = atomic(0) // never decreases

    fun addLast(element: E): Boolean {
        val curSize = size.value
        if (curSize == capacity) return false
        // TODO: you need to install the element and
        // TODO: increment the size atomically.
        // TODO: You are NOT allowed to use CAS2,
        // TODO: there is a more efficient and smarter solution!

        while (true) {
            val index = size.value
            when (val currentCellValue = array[index].value) {
                null -> {
                    val descriptor = AddDescriptor(index, element)
                    if (array[index].compareAndSet(null, descriptor)) {
                        descriptor.applyOperation()
                        return true
                    }
                    else {
                        continue
                    }
                }
                is DynamicArraySimplified<*>.AddDescriptor -> {
                    currentCellValue.applyOperation()
                    continue
                }
                else -> {
                    continue
                }
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

    private inner class AddDescriptor(
        val index: Int, val element: E
    ) {
        fun applyOperation() {
            // increase Size by 1
            /*
            while (true) {
                val currentSize = size.value
                if (size.compareAndSet(currentSize, currentSize+1)) break
            }*/
            size.compareAndSet(index, index+1)
            array[index].compareAndSet(this, element)
        }
    }
}