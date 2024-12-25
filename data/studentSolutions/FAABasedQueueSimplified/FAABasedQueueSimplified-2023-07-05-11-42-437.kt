//package day2

import day1.*
import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(15) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        while (true) {
            val curEnqIdx = enqIdx.getAndIncrement()
            if (infiniteArray[curEnqIdx].compareAndSet(null, element)) return
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        if (isEmpty()) return null
        while(true) {

            val curDeqIdx = deqIdx.getAndIncrement()
            if (infiniteArray[curDeqIdx].compareAndSet(null, POISONED)) {
                continue
            }
            return infiniteArray[curDeqIdx].value as E
        }
    }

    private fun isEmpty() : Boolean {
        while(true) {
            val curEnqIdx = enqIdx.value
            val curDeqIdx = deqIdx.value
            if (curEnqIdx != enqIdx.value) continue

            return curEnqIdx <= curDeqIdx
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
