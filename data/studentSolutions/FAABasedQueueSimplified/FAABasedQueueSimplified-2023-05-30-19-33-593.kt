//package day2

import day1.*
import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(1024) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.
        enqIdx.getAndIncrement()
        val i = enqIdx.value
//        enqIdx.value = i + 1
        // TODO: Atomically install the element into the cell
        // TODO: if the cell is not poisoned.
        if (!infiniteArray[i].compareAndSet(POISONED, element))
            infiniteArray[i].compareAndSet(infiniteArray[i].value, element)
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        // Is this queue empty?
        if (enqIdx.value <= deqIdx.value) return null
        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.
        deqIdx.getAndIncrement()
        val i = deqIdx.value
        // TODO: Try to retrieve an element if the cell contains an
        // TODO: element, poisoning the cell if it is empty.
        if (!infiniteArray[i].compareAndSet(null, POISONED)) {
            val result = infiniteArray[i].value as E?
            infiniteArray[i].compareAndSet(infiniteArray[i].value, null)
            return result
        }
        return null
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
