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
            if (infiniteArray[i].compareAndSet(null, element))
                return
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (!shouldTryToDequeue()) return null

            val i = deqIdx.getAndIncrement()

            // TODO: Try to retrieve an element if the cell contains an
            // TODO: element, poisoning the cell if it is empty.

            if (infiniteArray[i].compareAndSet(null, POISONED))
                continue

            return infiniteArray[i].value as E
        }
    }

    private fun shouldTryToDequeue(): Boolean {
        while (true) {
            val curEnqIdx = enqIdx.value
            val curDeqIdx = deqIdx.value

            if (curEnqIdx != enqIdx.value) continue
            return curDeqIdx >= curEnqIdx
        }
    }

}

// TODO: poison cells with this value.
private val POISONED = Any()
