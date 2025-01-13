//package day2

import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(30) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.
        while (true) {
            val i = enqIdx.getAndIncrement()
            // TODO: Atomically install the element into the cell
            // TODO: if the cell is not poisoned.
            // cas если там null то значит декью не мешал нам и можно положить
            if (infiniteArray[i].compareAndSet(null, element)) {
                return
            }
        }
//        infiniteArray[i].value = element
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
            // Is this queue empty?
            if (shouldNotTryToDeque()) return null
            while (true) {

            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.

            val i = deqIdx.getAndIncrement()
            // TODO: Try to retrieve an element if the cell contains an
            // TODO: element, poisoning the cell if it is empty.
            // cas на нулл тогда пойзнед
            if (infiniteArray[i].compareAndSet(null, POISONED)) {
                continue
            } else {
                return infiniteArray[i].value as E
            }
        }
    }

    private fun shouldNotTryToDeque(): Boolean {
        while (true) {
            val curEnqIdx = enqIdx.value
            val curDeqIdx = deqIdx.value
            if (curEnqIdx != enqIdx.value) continue
            return curEnqIdx <= curDeqIdx
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
