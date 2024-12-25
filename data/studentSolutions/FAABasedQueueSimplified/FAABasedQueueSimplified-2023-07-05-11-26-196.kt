//package day2

import day1.*
import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(15) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        while (true) {
            val index = enqIdx.getAndIncrement()
            if (infiniteArray[index].compareAndSet(null, element))
                return
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (!shouldDequeue())
                return null
            val lastEnq = enqIdx.value
            if (lastEnq <= deqIdx.value)
                return null
            val index = deqIdx.getAndIncrement()
            if (infiniteArray[index].compareAndSet(null, POISONED))
                continue
            return infiniteArray[index].value as E
        }
    }

    private fun shouldDequeue(): Boolean {
        while (true) {
            val lastDeq = deqIdx.value
            val lastEnq = enqIdx.value
            val curDeq = deqIdx.value
            if (curDeq != lastDeq)
                continue
            return lastEnq > lastDeq
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
