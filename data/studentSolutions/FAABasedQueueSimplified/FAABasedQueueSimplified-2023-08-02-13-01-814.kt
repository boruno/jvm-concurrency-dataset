package day2

import day1.*
import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(1024) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        while (true) {

            val i = enqIdx.incrementAndGet()
            if (infiniteArray[i].compareAndSet(null, element)) return
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        if (!shouldTryToDeque()) return null
        val i = deqIdx.value
        return infiniteArray[i].getAndSet(null) as E?
    }

    private fun shouldTryToDeque(): Boolean {
        val dq = deqIdx.value
        val enq = enqIdx.value
        if (dq == deqIdx.value && enqIdx.value == enq) {
            if (dq <= enq) {
                return true
            }
        }
        return false
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
