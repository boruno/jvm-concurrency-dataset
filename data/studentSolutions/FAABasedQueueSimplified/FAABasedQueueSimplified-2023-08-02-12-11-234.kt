package day2

import day1.*
import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(1024) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    private fun shouldTryDequeue(): Boolean {
        while (true) {
            val currentEnqIdx = enqIdx.value
            val currentDeqIdx = deqIdx.value
            if (currentEnqIdx != enqIdx.value) {
                continue
            }
            return currentDeqIdx >= currentEnqIdx
        }
    }

    override fun enqueue(element: E) {
        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.
        while (true) {
            val i = enqIdx.getAndIncrement()
            // TODO: Atomically install the element into the cell
            // TODO: if the cell is not poisoned.
            if (infiniteArray[i].compareAndSet(null, element)) {
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        // Is this queue empty?
        while (true) {
            // if (deqIdx.value >= enqIdx.value) return null
            if(!shouldTryDequeue()) return null
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
            val i = deqIdx.getAndIncrement()
            if (infiniteArray[i].compareAndSet(null, POISONED)) continue
            // TODO: Try to retrieve an element if the cell contains an
            // TODO: element, poisoning the cell if it is empty.
            return infiniteArray[i].value as E
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
