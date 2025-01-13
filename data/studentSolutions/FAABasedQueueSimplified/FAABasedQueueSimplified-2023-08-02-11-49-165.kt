//package day2

import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(1024) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        while (true) {
            val index = enqIdx.getAndIncrement()
            val cell = infiniteArray[index]
            if (cell.compareAndSet(null, element))
                return
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            // Is this queue empty?
            while (true) {
                val currentEnqIdx = enqIdx.value
                val currentDeqIdx = deqIdx.value
                if (currentDeqIdx != enqIdx.value)
                    continue
                else break
            }

            val index = deqIdx.getAndDecrement()
            val cell = infiniteArray[index]
            if (cell.compareAndSet(null, POISONED))
                continue

            return cell.value as E
        }
    }
}

private val POISONED = Any()
