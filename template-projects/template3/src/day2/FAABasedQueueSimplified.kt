package day2

import day1.*
import java.util.concurrent.atomic.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = AtomicReferenceArray<Any?>(1024) // conceptually infinite array
    private val enqIdx = AtomicLong(0)
    private val deqIdx = AtomicLong(0)

    override fun enqueue(element: E) {
        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.
        val i = enqIdx.get()
        enqIdx.set(i + 1)
        // TODO: Atomically install the element into the cell
        // TODO: if the cell is not poisoned.
        infiniteArray.set(i.toInt(), element)
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        // Is this queue empty?
        if (enqIdx.get() <= deqIdx.get()) return null
        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.
        val i = deqIdx.get()
        deqIdx.set(i + 1)
        // TODO: Try to retrieve an element if the cell contains an
        // TODO: element, poisoning the cell if it is empty.
        return infiniteArray.get(i.toInt()) as E
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
