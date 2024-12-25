//package day2

import day1.*
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
            if (!shouldDequeue()) continue

            val i = deqIdx.incrementAndGet()
            if (infiniteArray[i].compareAndSet(null, POISONED)) continue
            return infiniteArray[i].value as E?
        }
    }

    private fun shouldDequeue(): Boolean {
        while (true) {
            val enqIdxSnap = enqIdx
            val deqIdxSnap = deqIdx
            if (enqIdxSnap.value != enqIdx.value) continue
            return deqIdxSnap.value >= enqIdxSnap.value
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
