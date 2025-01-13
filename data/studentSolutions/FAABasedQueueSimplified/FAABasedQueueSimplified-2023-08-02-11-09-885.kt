//package day2

import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(1024) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        do {
            val i = enqIdx.getAndIncrement()
        }
        while(!infiniteArray[i].compareAndSet(null, element))
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (enqIdx.value <= deqIdx.value) return null
            val i = deqIdx.getAndIncrement()
            val slot = infiniteArray[i]
            if (slot.compareAndSet(null, POISONED))
                continue
            return slot.value as E
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
