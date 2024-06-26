package day2

import day1.*
import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(1024) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        do {
            val enqueueIndex = enqIdx.getAndIncrement()
        } while (!infiniteArray[enqueueIndex].compareAndSet(null, element))
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            // Is this queue empty?
            if (deqIdx.value >= deqIdx.value)
                return null // empty

            val dequeueIndex = deqIdx.getAndIncrement()

            if (infiniteArray[dequeueIndex].compareAndSet(null, POISONED))
                continue

            return infiniteArray[dequeueIndex].value as E
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
