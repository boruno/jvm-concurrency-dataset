//package day2

import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(100) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)
    override fun enqueue(element: E) {
        while (true) {
            val index = enqIdx.getAndIncrement()
            if (infiniteArray[index].compareAndSet(null, element)) {
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            val index = deqIdx.getAndIncrement()
            if (index >= enqIdx.value) {
                return null
            }

            if (infiniteArray[index].compareAndSet(null, POISONED))
                continue

            val value = infiniteArray[index].value
            if (value == POISONED) continue

            return infiniteArray[index] as E
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()