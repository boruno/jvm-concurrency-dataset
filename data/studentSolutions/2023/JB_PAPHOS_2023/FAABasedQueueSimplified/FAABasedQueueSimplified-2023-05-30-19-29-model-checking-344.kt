package day2

import day1.*
import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E: Any> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(1024) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    // Obstruction-free, not legally Lock-free

    override fun enqueue(element: E) {
        while (true) {
            val i = enqIdx.getAndIncrement()
            val queueCell = infiniteArray[i]
            if (queueCell.compareAndSet(null, element)) {
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            // TODO I don't trust this emptyness check
            val i = deqIdx.incrementAndGet()
            if (i > enqIdx.value) {
                enqIdx.incrementAndGet()
                return null
            }
            val queueCell = infiniteArray[i]
            if (!queueCell.compareAndSet(null, POISONED)) {
                return queueCell.value as E
            }
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
