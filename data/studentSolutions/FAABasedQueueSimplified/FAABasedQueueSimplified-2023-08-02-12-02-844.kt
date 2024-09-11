package day2

import day1.Queue
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(1024) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        while (true) {
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
            val i = enqIdx.getAndIncrement()
            // TODO: Atomically install the element into the cell
            // TODO: if the cell is not poisoned.
            if (infiniteArray[i].compareAndSet(null, element)) break
        }
    }

    override fun dequeue(): E? {
        while (true) {
            // Is this queue empty?
            if (!shouldTryToDequeue()) return null
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
            val i = deqIdx.getAndIncrement()
            // TODO: Try to retrieve an element if the cell contains an
            // TODO: element, poisoning the cell if it is empty.
            if (infiniteArray[i].compareAndSet(null, POISONED)) continue
            @Suppress("UNCHECKED_CAST")
            return infiniteArray[i].value as E
        }
    }

    private fun shouldTryToDequeue(): Boolean {
        while (true) {
            val enqIdxSnapshot = enqIdx.value
            val deqIdxSnapshot = deqIdx.value
            if (deqIdxSnapshot != deqIdx.value) continue
            return deqIdxSnapshot >= enqIdxSnapshot
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
