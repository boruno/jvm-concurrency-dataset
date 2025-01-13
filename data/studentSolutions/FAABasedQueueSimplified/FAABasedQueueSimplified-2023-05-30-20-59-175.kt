//package day2

import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(1024) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.
        while (true) {
            val i = enqIdx.getAndIncrement()
            val item = infiniteArray[i]
            if (item.compareAndSet(null, element)) { return }
        }
            // TODO: Atomically install the element into the cell
            // TODO: if the cell is not poisoned.

        }


    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        // Is this queue empty?
        while (true) {
            val i = deqIdx.getAndIncrement()
            if (enqIdx.value <= deqIdx.value) return null
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
            val item = infiniteArray[i]
            if (item.compareAndSet(null, POISONED))
            {
                continue
            }
            // TODO: Try to retrieve an element if the cell contains an
            // TODO: element, poisoning the cell if it is empty.
            return item.value as E
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()

//FAA = getAndIncrement