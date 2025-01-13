//package day2

import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(150) // conceptually infinite array
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
        val (enq, deq) = fetchSnapshot()
        if (enq <= deq) return null

        while(true) {
            val idx = deqIdx.getAndIncrement()
            if (infiniteArray[idx].compareAndSet(null, POISONED)) {
                continue
            }
            return infiniteArray[idx].value as E
        }
    }

    private fun fetchSnapshot() : Pair<Int, Int> {
        while(true) {
            val curEnqIdx = enqIdx.value
            val curDeqIdx = deqIdx.value
            if (curEnqIdx != enqIdx.value) continue
            return curEnqIdx to curDeqIdx
        }
    }

}

// TODO: poison cells with this value.
private val POISONED = Any()
