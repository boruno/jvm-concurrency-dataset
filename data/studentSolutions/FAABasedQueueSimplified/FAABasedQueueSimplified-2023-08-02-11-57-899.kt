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
            if (!shouldDequeue()) return null

            val i = deqIdx.incrementAndGet()
            return if (infiniteArray[i].compareAndSet(null, POISONED)) {
                null
            } else {
                infiniteArray[i].value as E?
            }
        }
    }

    fun shouldDequeue(): Boolean {
        while (true) {
            val deqIdxSnap = deqIdx
            val enqIdxSnap = enqIdx
            if (enqIdxSnap.value != enqIdx.value) continue
            return enqIdxSnap.value <= deqIdxSnap.value
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
