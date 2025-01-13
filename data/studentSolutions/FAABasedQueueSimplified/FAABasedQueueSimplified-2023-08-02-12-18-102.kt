//package day2

import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(1024) // conceptually infinite array
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
            // Is this queue empty?
//            if (deqIdx.value >= enqIdx.value) return null
            if (!shouldTryDeque()) {
                return null
            }

            val i = deqIdx.getAndIncrement()
            val element = infiniteArray[i].getAndSet(POISONED)
            if (element != null && element != POISONED) {
                return element as E
            }
        }
    }

    private fun shouldTryDeque(): Boolean {
        while (true) {
            val curEnqIdx = enqIdx.value
            val curDeqIdx = deqIdx.value

            if (curDeqIdx != deqIdx.value) continue
            return curEnqIdx >= curDeqIdx
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
