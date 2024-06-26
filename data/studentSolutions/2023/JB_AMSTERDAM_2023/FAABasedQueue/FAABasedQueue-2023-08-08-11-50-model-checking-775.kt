package day2

import day1.*
import java.util.concurrent.atomic.*

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size `Segment`s.
class FAABasedQueue<E> : Queue<E> {
    private val infiniteArray = AtomicReferenceArray<Any?>(1024) // conceptually infinite array
    private val enqueueIndex = AtomicLong(0)
    private val dequeueIndex = AtomicLong(0)

    override tailrec fun enqueue(element: E) {
        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.
        // TODO: Atomically install the element into the cell
        // TODO: if the cell is not poisoned.
        if (!infiniteArray.compareAndSet(enqueueIndex.getAndIncrement().toInt(), null, element))
            return enqueue(element)
    }

    @Suppress("UNCHECKED_CAST")
    override tailrec fun dequeue(): E? {
        if (shouldTryToDequeue()) return null
        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.
        val i = dequeueIndex.getAndIncrement().toInt()
        if (infiniteArray.compareAndSet(i, null, brokenValue)) return dequeue()
        // TODO: Try to retrieve an element if the cell contains an
        // TODO: element, poisoning the cell if it is empty.
        return infiniteArray[i] as E
    }

    private fun shouldTryToDequeue(): Boolean =
        dequeueIndex.doubleCollect(enqueueIndex) { left, right -> left <= right }
}

private val brokenValue = Any()

private class Segment(val id: Long) {
    val next = AtomicReference<Segment?>(null)
    val cells = AtomicReferenceArray<Any?>(SEGMENT_SIZE)
}

inline fun  <R> AtomicLong.doubleCollect(other: AtomicLong, mapper: (Long, Long) -> R): R {
    while (true) {
        val left = get()
        val right = other.get()
        if (left != get()) continue
        return mapper(left, right)
    }
}

// DO NOT CHANGE THIS CONSTANT
private const val SEGMENT_SIZE = 2
