//package day2

import day1.*
import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(1024) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        while (true) {
            val i = enqIdx.addAndGet(1)
            if (infiniteArray[i].compareAndSet(null, element)) return
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            val deq = deqIdx.value
            val enq = enqIdx.value
            if (deq <= enq) return null
            val i = deqIdx.addAndGet(1)
            if (!infiniteArray[i].compareAndSet(null, POISONED)) {
               return infiniteArray[i].value as E
            }
        }

    }
}

private val POISONED = Any()
