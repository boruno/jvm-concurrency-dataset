//package day2

import day1.*
import kotlinx.atomicfu.*


class FAABasedQueueSimplified<E> : Queue<E> {

    private val infiniteArray = atomicArrayOfNulls<Any?>(1024) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        while (true) {
            val i = enqIdx.getAndIncrement()
            infiniteArray[i].compareAndSet(null, element)
        }
    }


    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (!shouldTryToDequeue()) return null
//        // Is this queue empty?
//        if (enqIdx.value <= deqIdx.value) return null
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
//        val i = deqIdx.value
//        deqIdx.value = i + 1

            val i = deqIdx.getAndIncrement()


            if (infiniteArray[i].compareAndSet(null, POISONED))
                continue


            // TODO: Try to retrieve an element if the cell contains an
            // TODO: element, poisoning the cell if it is empty.
            return infiniteArray[i].value as E
        }
    }

    private fun shouldTryToDequeue(): Boolean {
        return true
//        while (true) {
//            val curEnqIdx = enqIdx
//            val curDeqIdx = deqIdx
//
//            if (curDeqIdx != deqIdx) continue
//            return curDeqIdx.value >= curEnqIdx.value
//        }
    }

}

// TODO: poison cells with this value.
private val POISONED = Any()
