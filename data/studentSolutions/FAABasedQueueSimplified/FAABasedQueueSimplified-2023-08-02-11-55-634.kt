//package day2

import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(1024) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.
        // TODO: Atomically install the element into the cell
        // TODO: if the cell is not poisoned.

        while (true) {
            val i = enqIdx.getAndIncrement()

            if (infiniteArray[i].compareAndSet(null, element)) {
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.
        // TODO: Try to retrieve an element if the cell contains an
        // TODO: element, poisoning the cell if it is empty.
        while (true) {
            // Is this queue empty?
            if (shouldTryToDequeue()/*enqIdx.value <= deqIdx.value*/) return null

            val i = deqIdx.getAndIncrement()
            if (infiniteArray[i].value != null) {
                return infiniteArray[i].value as E
            } else {
                if (infiniteArray[i].compareAndSet(null, POISONED)) {
                    continue
                } else {
                    return infiniteArray[i].value as E
                }
            }
        }
    }

    private fun shouldTryToDequeue(): Boolean {
        while (true) {
            val curEnqIdx = enqIdx.value
            val curDeqIdx = deqIdx.value
            if (curEnqIdx != enqIdx.value) continue
            return curEnqIdx > curDeqIdx
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
