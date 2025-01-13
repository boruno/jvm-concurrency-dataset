//package day2

import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(1024) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        while (true) {
            // Increment the counter atomically via Fetch-and-Add.
            // Use `getAndIncrement()` function for that.
            val i = enqIdx.incrementAndGet()
            // Atomically install the element into the cell
            // if the cell is not poisoned.
            if (infiniteArray[i].compareAndSet(null, element)) return
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            // Is this queue empty?
            if (enqIdx.value <= deqIdx.value) return null
            // Increment the counter atomically via Fetch-and-Add.
            // Use `getAndIncrement()` function for that.
            val i = deqIdx.incrementAndGet()
            // Try to retrieve an element if the cell contains an
            // element, poisoning the cell if it is empty.
            if (infiniteArray[i].compareAndSet(null, POISONED)) continue
            return infiniteArray[i].value as E
        }
    }
}

// poison cells with this value.
private val POISONED = Any()
