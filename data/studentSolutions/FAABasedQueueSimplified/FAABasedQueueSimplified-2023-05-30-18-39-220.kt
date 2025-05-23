//package day2

import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(1024) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        while (true) {
            val i = enqIdx.incrementAndGet()
            if (infiniteArray[i].compareAndSet(null, POISONED)) return
        }
        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.
        // TODO: Atomically install the element into the cell
        // TODO: if the cell is not poisoned.
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            // Is this queue empty?
            if (deqIdx.value >= enqIdx.value) return null
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
            val i = deqIdx.incrementAndGet()
            // TODO: Try to retrieve an element if the cell contains an
            // TODO: element, poisoning the cell if it is empty.
            if (infiniteArray[i].compareAndSet(null, POISONED)) continue
            return infiniteArray[i].value as E
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
