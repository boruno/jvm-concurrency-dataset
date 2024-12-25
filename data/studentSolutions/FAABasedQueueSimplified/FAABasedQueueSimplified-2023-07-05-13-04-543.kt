//package day2

import day1.*
import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(15) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        while (true) {
            val i = enqIdx.getAndIncrement()
            if (infiniteArray[i].compareAndSet(null, element)) {
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (enqIdx.value <= deqIdx.value) return null
            val i = deqIdx.getAndIncrement()
            val value = infiniteArray[i].value
            if (!infiniteArray[i].compareAndSet(null, POISONED)) {
                return value as E
            }
        }
    }
}

private val POISONED = Any()
