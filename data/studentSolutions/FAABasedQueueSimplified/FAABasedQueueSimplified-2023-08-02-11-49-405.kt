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

    private inline fun isEmpty(): Boolean {
        while (true) {
            val enq = enqIdx.value
            val deq = deqIdx.value
            if (enq != enqIdx.value) continue
            return deq >= enq
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
//            if (isEmpty()) return null
            if (enqIdx.value <= deqIdx.value) return null
            val i = deqIdx.getAndIncrement()
            if (infiniteArray[i].compareAndSet(null, POISONED)) continue
            return infiniteArray[i].value as E
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
