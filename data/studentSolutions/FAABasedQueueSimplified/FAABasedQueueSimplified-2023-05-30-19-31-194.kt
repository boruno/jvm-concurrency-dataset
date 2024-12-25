//package day2

import day1.*
import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(1024) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        val i = enqIdx.getAndIncrement()
        infiniteArray[i].value = element
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        if (enqIdx.value <= deqIdx.value) return null
        val i = deqIdx.getAndIncrement()
        val element = infiniteArray[i].value as E
        if (element == null) {
            infiniteArray[i].compareAndSet(null, POISONED)
            return dequeue() // Retry dequeueing if the cell was poisoned
        }
        return element
    }
}

private val POISONED = Any()
