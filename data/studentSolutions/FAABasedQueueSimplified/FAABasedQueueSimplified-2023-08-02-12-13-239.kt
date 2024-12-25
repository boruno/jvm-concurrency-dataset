//package day2

import day1.*
import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(1024) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        while ( true) {
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
            //        val i = enqIdx.value
            //        enqIdx.value = i + 1
            // TODO: Atomically install the element into the cell
            // TODO: if the cell is not poisoned.
            //        infiniteArray[i].value = element
            val i = enqIdx.getAndIncrement()
            if (infiniteArray[i].compareAndSet(null, element)) return
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            // Is this queue empty?
//            if (enqIdx.value <= deqIdx.value) return null
            if (!shouldTryToDequeue()) return null
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
            //        val i = deqIdx.value
            //        deqIdx.value = i + 1
            val i = deqIdx.incrementAndGet()
            // TODO: Try to retrieve an element if the cell contains an
            // TODO: element, poisoning the cell if it is empty.
            if (infiniteArray[i].compareAndSet(null, POISONED)) {
                continue
            } else {
                return infiniteArray[deqIdx.value].value as E
            }

//            return infiniteArray[i].value as E
        }
    }

    private fun shouldTryToDequeue(): Boolean {
        while (true) {
            val currentEnqueueIndex = enqIdx.value
            val currentDequeueIndex = deqIdx.value
            if (currentEnqueueIndex != enqIdx.value) continue
            return currentDequeueIndex >= currentEnqueueIndex
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
