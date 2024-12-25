//package day2

import day1.*
import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(15) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        while (true) {
            // Increment the counter atomically via Fetch-and-Add.
            val i = enqIdx.incrementAndGet()
            // Atomically install the element into the cell
            // if the cell is not poisoned.
            if (!infiniteArray[i].compareAndSet(POISONED, element)) {
                infiniteArray[i].value = element
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        // Is this queue empty?
        if (!shouldTryToDeque()) return null

        // Increment the counter atomically via Fetch-and-Add.
        // Use `getAndIncrement()` function for that.
        while (true) {
            val i = deqIdx.getAndIncrement()
            // Try to retrieve an element if the cell contains an
            // element, poisoning the cell if it is empty.
            if (!infiniteArray[i].compareAndSet(null, POISONED)) {
                return infiniteArray[i].value as E
            }
        }
    }

    private fun shouldTryToDeque(): Boolean {
        while (true) {
            val curDeqIdx = deqIdx.value
            val curEnqIdx = enqIdx.value
            if (curDeqIdx != deqIdx.value) continue
            return curDeqIdx >= curEnqIdx
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
