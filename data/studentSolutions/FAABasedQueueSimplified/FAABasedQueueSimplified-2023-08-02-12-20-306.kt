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
        while (true) {
            // old value
            val i = enqIdx.getAndIncrement()
            //enqIdx.value = i + 1
            // TODO: Atomically install the element into the cell
            // TODO: if the cell is not poisoned.
//            val value = infiniteArray[i].value
            val value = infiniteArray[i].value
            if (value == POISONED) break
            if (infiniteArray[i].compareAndSet(value, element)) return
            else break
        }
    }

    fun shouldNotTryToDeque(): Boolean {
        while (true) {
            val curEnqInx = enqIdx.value
            val curDeqIdx = deqIdx.value
            if (curEnqInx != enqIdx.value) continue
            return curDeqIdx >= curEnqInx
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        // Is this queue empty?

        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.
        while (true) {
            if (shouldNotTryToDeque()) return null
            val i = deqIdx.getAndIncrement()
            // deqIdx.value = i + 1
            // TODO: Try to retrieve an element if the cell contains an
            // TODO: element, poisoning the cell if it is empty.

            if (infiniteArray[i].compareAndSet(null, POISONED)) continue
            return infiniteArray[i].value as E
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
