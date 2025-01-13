//package day2

import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(15) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        while (true) {
            val i = enqIdx.getAndIncrement()
            if (!infiniteArray[i].compareAndSet(null, element)) continue
            return
        }
        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.
        // TODO: Atomically install the element into the cell
        // TODO: if the cell is not poisoned.
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
//            if (!isDequeuePossible()) {
//                return null
//            }
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
            // TODO: Try to retrieve an element if the cell contains an
            // TODO: element, poisoning the cell if it is empty.
            val i = deqIdx.getAndIncrement()
            if (!infiniteArray[i].compareAndSet(null, POISONED)) {
                return infiniteArray[i].value as E
            } else {
                continue
            }
        }
    }

    private fun isDequeuePossible(): Boolean {
        while (true) {
            val curDeqIdx = deqIdx.value
            val curEnqIdx = enqIdx.value
            if (curDeqIdx != deqIdx.value) continue
            return curEnqIdx > curDeqIdx
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
