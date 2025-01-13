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

//        infiniteArray[currentEnqIndex].value = element
        // TODO: Atomically install the element into the cell
        // TODO: if the cell is not poisoned.
        if (infiniteArray[i].value == POISONED) {
            return
        }

        infiniteArray[i].compareAndSet(null, element)
//        infiniteArray[i].value = element
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        // Is this queue empty?
//        if (enqIdx.value <= deqIdx.value) return null
        if (isEmpty()) return null
        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.
//        val i = deqIdx.value
        val i = deqIdx.getAndIncrement()

//        deqIdx.value = i + 1
        // TODO: Try to retrieve an element if the cell contains an
        // TODO: element, poisoning the cell if it is empty.

        if (infiniteArray[i].compareAndSet(null, POISONED)) {
            return null
        }

        return infiniteArray[i].value as E
    }

    private fun isEmpty(): Boolean {
        while (true) {
            val currentDeqIndex = deqIdx.value
            val currentEnqIndex = enqIdx.value
            if (currentDeqIndex.compareTo(deqIdx.value) != 0) continue
            return currentDeqIndex >= currentEnqIndex
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
