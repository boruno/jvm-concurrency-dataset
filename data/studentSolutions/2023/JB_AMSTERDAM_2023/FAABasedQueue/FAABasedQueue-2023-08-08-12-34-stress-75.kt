package day2

import day1.*
import java.util.concurrent.atomic.*

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size `Segment`s.
class FAABasedQueue<E> : Queue<E> {

    private val infiniteArray = AtomicReferenceArray<Any?>(1024) // conceptually infinite array
    private val enqIdx = AtomicLong(0)
    private val deqIdx = AtomicLong(0)

    override fun enqueue(element: E) {
        while (true) {
            val eqIndex = enqIdx.getAndIncrement()
            if (infiniteArray.compareAndSet(eqIndex.toInt(), null, element)) {
                return;
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            // Is this queue empty?
            if (!shouldTryToDequeue()) return null
            val deqIndex = deqIdx.getAndIncrement()
            if (infiniteArray.compareAndSet(deqIndex.toInt(), null, POISONED)) {
                continue;
            }
            return infiniteArray[deqIndex.toInt()] as E
        }

    }

    private fun shouldTryToDequeue(): Boolean {
        val currEnqIdx = enqIdx.get()
        val currDeqIdx = deqIdx.get()

        if (currEnqIdx != enqIdx.get()) return false;
        return currEnqIdx < currDeqIdx
    }
}

private class Segment(val id: Long) {
    val next = AtomicReference<Segment?>(null)
    val cells = AtomicReferenceArray<Any?>(SEGMENT_SIZE)
}

private val POISONED = Any()
// DO NOT CHANGE THIS CONSTANT
private const val SEGMENT_SIZE = 2
