//package day2

import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(1024) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        while (true) {
            val i = enqIdx.getAndIncrement()
            if (infiniteArray[i].compareAndSet(null, element)) return
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            // Is this queue empty?
            if (!shouldTryToDequeue()) return null
            val i = deqIdx.getAndIncrement()
            if (infiniteArray[i].compareAndSet(null, POISONED)) continue
            return infiniteArray[i].value as E
        }
    }

    private fun shouldTryToDequeue(): Boolean {
        while (true) {
            val curEnqIdx = enqIdx.value
            val curDeqIdx = deqIdx.value
            if (curEnqIdx != enqIdx.value) continue
            return curEnqIdx <= curDeqIdx
        }
    }
}

private val POISONED = Any()
