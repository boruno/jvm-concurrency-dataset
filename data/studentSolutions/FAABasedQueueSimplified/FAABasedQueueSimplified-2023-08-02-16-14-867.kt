//package day2

import day1.*
import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(1024) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)
    override fun enqueue(element: E) {
        val i = enqIdx.getAndIncrement()
        if (infiniteArray[i].compareAndSet(null, element)) {
            return
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
            // TODO: Try to retrieve an element if the cell contains an
            // TODO: element, poisoning the cell if it is empty.
            // TODO: Atomically return the element.
            if (deqIdx.value >= enqIdx.value) return null
            val i = deqIdx.incrementAndGet()
            if (!infiniteArray[i].compareAndSet(null, POISONED)) {
                return infiniteArray[i].value as E
            }
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
