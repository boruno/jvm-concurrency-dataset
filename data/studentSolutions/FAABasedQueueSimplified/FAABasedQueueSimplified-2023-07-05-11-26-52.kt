//package day2

import day1.*
import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(15) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        while (true) {
            val i = enqIdx.incrementAndGet();
            if (infiniteArray[i].compareAndSet(null, element)) {
                return;
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
//            if (enqIdx.value <= deqIdx.value) return null

            val i = deqIdx.incrementAndGet()

            deqIdx.value = i + 1
            if (infiniteArray[i].compareAndSet(null, POISONED))
                continue

            return infiniteArray[i].value as E
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
