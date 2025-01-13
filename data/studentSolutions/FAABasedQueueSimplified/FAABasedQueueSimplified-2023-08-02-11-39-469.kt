//package day2

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
//            if (deqIdx.value >= enqIdx.value)
//                return null // empty

            if (isEmpty())
                return null

            val dequeueIndex = deqIdx.getAndIncrement()

            if (infiniteArray[dequeueIndex].compareAndSet(null, POISONED))
                continue

            return infiniteArray[dequeueIndex].value as E
        }
    }

    fun isEmpty(): Boolean {
        while (true) {
            val currentEnqueueIndex = enqIdx.value
            val currentDequeueIndex = deqIdx.value

            if (currentDequeueIndex != deqIdx.value)
                continue

            return currentDequeueIndex >= currentEnqueueIndex
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
