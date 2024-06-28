package day2

import day1.*
import kotlinx.atomicfu.*

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size segments.
class FAABasedQueue<E> : Queue<E> {
    private val SEGM_SIZE = 64
    private class dumby(val arr: AtomicArray<Any?> = atomicArrayOfNulls(64)) {
    }
    private val enqIdx = atomic(-1)
    private val deqIdx = atomic(-1)
    private var almostInfArray = MSQueue<dumby>()
    override fun enqueue(element: E) {
        while (true) {
            val i = enqIdx.getAndIncrement()
            if (i % SEGM_SIZE == 0) {
                almostInfArray.enqueue(dumby())
            }
            val s = almostInfArray.tail.value.element!!
            if (s.arr[i % SEGM_SIZE].compareAndSet(null, element)) {
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (deqIdx.value >= enqIdx.value) {
                return null
            }
            val i = deqIdx.getAndIncrement()
            if (i % SEGM_SIZE == 0) {
                almostInfArray.dequeue()
            }
            val s = almostInfArray.head.value.element!!
            if (s.arr[i % SEGM_SIZE].compareAndSet(null, POISONED)) {
                continue
            }
            return s.arr[i % SEGM_SIZE].value as E?
        }
    }
}

private val POISONED = Any()

