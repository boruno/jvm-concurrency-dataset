//package day2

import day1.*
import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(1024) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        do {
            val i = enqIdx.getAndIncrement()
        } while (!infiniteArray[i].compareAndSet(null, element))
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {

        while (true) {
            // Is this queue empty?
            if (enqIdx.value <= deqIdx.value)
                return null

            val i = deqIdx.getAndIncrement()

            if (infiniteArray[i].compareAndSet(null, POISONED))
                continue

            return infiniteArray[i].value as E
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
