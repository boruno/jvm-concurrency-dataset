//package day2

import java.util.concurrent.atomic.*

class FAABasedQueue<E> : Queue<E> {
    private val enqIdx = AtomicLong(0)
    private val deqIdx = AtomicLong(0)
    private val infiniteArray = InfiniteArray()

    override tailrec fun enqueue(element: E) {
        var tail = infiniteArray.tail.get()
        val i = enqIdx.getAndIncrement()
        val segmentId = i / SEGMENT_SIZE
        val iModulo = (i % SEGMENT_SIZE).toInt()
        while (tail.id < segmentId) {
            val next = tail.next.get()
            if (next == null) {
                val newReadySegment = Segment(tail.id + 1)
                if (tail.next.compareAndSet(null, newReadySegment)) {
                    infiniteArray.tail.compareAndSet(tail, newReadySegment)
                }
            } else {
                infiniteArray.tail.compareAndSet(tail, next)
            }
            tail = infiniteArray.tail.get()
        }
        if (!tail.cells.compareAndSet(iModulo, null, element)) return enqueue(element)
    }

    @Suppress("UNCHECKED_CAST")
    override tailrec fun dequeue(): E? {
        if (deqIdx.get() >= enqIdx.get()) return null
        var head = infiniteArray.head.get()
        val i = deqIdx.getAndIncrement()
        val segmentId = i / SEGMENT_SIZE
        while (head.id < segmentId) {
            val nextHead = head.next.get() ?: return null
            infiniteArray.head.compareAndSet(head, nextHead)
            head = infiniteArray.head.get()
        }
        val iModulo = (i % SEGMENT_SIZE).toInt()
        if (head.cells.compareAndSet(iModulo, null, POISONED)) return dequeue()
        return (head.cells[iModulo] as E).also { head.cells[iModulo] = null }
    }
}

private class InfiniteArray(startId: Long = 0) {
    val head = AtomicReference(Segment(startId))
    val tail = AtomicReference(head.get())
}

private class Segment(val id: Long) {
    val next = AtomicReference<Segment?>(null)
    val cells = AtomicReferenceArray<Any?>(SEGMENT_SIZE)
}

// DO NOT CHANGE THIS CONSTANT
private const val SEGMENT_SIZE = 2

private val POISONED = Any()

