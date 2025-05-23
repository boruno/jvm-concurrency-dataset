//package day2

import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(100) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)
    override fun enqueue(element: E) {
        while (true) {
            val index = enqIdx.getAndIncrement()
            if (infiniteArray[index].compareAndSet(null, element)) {
                return
            }
        }
    }

    private fun tryDequeueElement(): Boolean {
        while (true) {
            val d = deqIdx.value
            val e = enqIdx.value
            if (d != deqIdx.value) continue
            return d <= e
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if(!tryDequeueElement())
                return null

            val index = deqIdx.getAndIncrement()

            if (infiniteArray[index].compareAndSet(null, POISONED))
                continue

            return infiniteArray[index].value as E
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()