//package day2

import day1.*
import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(1024) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        // TODO: Increment the counter atomically via Fetch-and-Add. Use `getAndIncrement()` function for that.
        val i = enqIdx.getAndIncrement()
        // TODO: Atomically install the element into the cell if the cell is not poisoned.
        while (!infiniteArray[i].compareAndSet(null, element)) {
            // Retry until the cell is successfully updated
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        // Is this queue empty?
        if (enqIdx.value <= deqIdx.value) return null
        // TODO: Increment the counter atomically via Fetch-and-Add. Use `getAndIncrement()` function for that.
        val i = deqIdx.getAndIncrement()
        // TODO: Try to retrieve an element if the cell contains an element, poisoning the cell if it is empty.
        val element = infiniteArray[i].getAndSet(POISONED) as E

        return element
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
