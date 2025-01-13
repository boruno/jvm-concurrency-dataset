//package day2

import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(1024) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.
        //val i = enqIdx.value
        //enqIdx.value = i + 1
        while (true) {
            val i = enqIdx.getAndIncrement()
            // TODO: Atomically install the element into the cell
            // TODO: if the cell is not poisoned.
            if (infiniteArray[i].compareAndSet(null, element)) return
        }
        //infiniteArray[i].value = element
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        // Is this queue empty?
        while (true) {
            if (shouldTryToDequeue()) return null
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
            //val i = deqIdx.value
            //deqIdx.value = i + 1
            val i = deqIdx.getAndIncrement()
            // TODO: Try to retrieve an element if the cell contains an
            // TODO: element, poisoning the cell if it is empty.
            val elem = infiniteArray[i].value as E
            if (infiniteArray[i].compareAndSet(null, POISONED)) continue;
            return elem
        }
    }

    fun shouldTryToDequeue(): Boolean {
        while (true)
        {
            val curEnqInd = enqIdx.value;
            val curDeqInd = deqIdx.value;
            if (curEnqInd != enqIdx.value) continue
            return curDeqInd >= curEnqInd
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
