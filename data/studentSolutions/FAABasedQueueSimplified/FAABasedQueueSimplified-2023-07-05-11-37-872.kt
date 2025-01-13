//package day2

import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(15) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.
//        val i = enqIdx.value
//        enqIdx.value = i + 1
        val i = enqIdx.getAndIncrement()
        // TODO: Atomically install the element into the cell
        // TODO: if the cell is not poisoned.
//        infiniteArray[i].value = element
        if (!infiniteArray[i].compareAndSet(null, element)) enqueue(element)
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        // Is this queue empty?
        if (enqIdx.value <= deqIdx.value) return null
        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.
//        val i = deqIdx.value
//        deqIdx.value = i + 1
        val i = enqIdx.getAndIncrement()
        val fetchedValue = infiniteArray[i].getAndSet(POISONED) as E?
        if (fetchedValue == null) dequeue()
        // TODO: Try to retrieve an element if the cell contains an
        // TODO: element, poisoning the cell if it is empty.
//        return infiniteArray[i].value as E
        return fetchedValue
    }

    private fun shouldTryToDequeue(): Boolean {
        while (true) {
            val curDeqIndex = deqIdx.value
            val curEnqIndex = enqIdx.value
            if (curDeqIndex != deqIdx.value) continue
            return curDeqIndex >= curEnqIndex
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
