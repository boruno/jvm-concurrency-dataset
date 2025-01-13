//package day2

import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(15) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        while (true) {
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
            val i = enqIdx.getAndIncrement()
            // TODO: Atomically install the element into the cell
            // TODO: if the cell is not poisoned.
            if (infiniteArray[i].compareAndSet(null, element)) {
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
//        while (true) {
        if (isQueueEmpty()) return null
        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.
        val i = deqIdx.getAndIncrement()
        // TODO: Try to retrieve an element if the cell contains an
        // TODO: element, poisoning the cell if it is empty.
        if (infiniteArray[i].compareAndSet(null, POISONED)) return null
        return infiniteArray[i].value as E
//        }
    }

    private fun isQueueEmpty(): Boolean {
        while (true) {
            val currentDeqIdx = deqIdx.value
            val currentEnqIdx = enqIdx.value
            if (currentDeqIdx != deqIdx.value) continue
            return currentDeqIdx >= currentEnqIdx
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
