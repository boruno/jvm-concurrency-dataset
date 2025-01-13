//package day2

import kotlinx.atomicfu.*

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size segments.
class FAABasedQueue<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(1024) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)


    override fun enqueue(element: E) {
        //TODO("Implement me!")
        while (true) {
            val i = enqIdx.value
            enqIdx.value = i + 1
            infiniteArray[i].value = element
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        //TODO("Implement me!")
        while (true) {
            if (enqIdx.value <= deqIdx.value) return null
            val i = deqIdx.value
            deqIdx.value = i + 1
            return infiniteArray[i].value as E
        }
    }
}