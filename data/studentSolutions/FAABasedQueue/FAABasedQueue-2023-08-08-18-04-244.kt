//package day2

import java.util.concurrent.atomic.*

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size `Segment`s.
class FAABasedQueue<E> : Queue<E> {
    private val enqIdx = AtomicLong(0)
    private val deqIdx = AtomicLong(0)
    private val infiniteArray = InfiniteArray()

    override fun enqueue(element: E) {
        val i = enqIdx.getAndIncrement()
        val tail = infiniteArray.tail.get()
        val segment = infiniteArray.getOrCreateSegment(i / SEGMENT_SIZE, tail)
        val iModulo = (i % SEGMENT_SIZE).toInt()
        if (!segment.cells.compareAndSet(iModulo, null, element)) return enqueue(element)
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        if (deqIdx.get() >= enqIdx.get()) return null
        val i = deqIdx.getAndIncrement()
        val head = infiniteArray.head.get()
        val segment = infiniteArray.getOrCreateSegment(i / SEGMENT_SIZE, head)
        val iModulo = (i % SEGMENT_SIZE).toInt()
        if (segment.cells.compareAndSet(iModulo, null, POISONED)) return dequeue()
        return (segment.cells[iModulo] as E).also { segment.cells[iModulo] = null }
    }
}

private class InfiniteArray(startId: Long = 0) {
    val head = AtomicReference(Segment(startId))
    val tail = AtomicReference(head.get())
    
    fun getOrCreateSegment(id: Long, start: Segment): Segment {
        var currentSegment = start
        while (true) {
            if (currentSegment.id == id) {
                return currentSegment
            }
            val next = currentSegment.next.get()
            if (next != null) {
                currentSegment = next
            } else {
                currentSegment.next.compareAndSet(null, Segment(id + 1))
            }
        }
    }
}

private class Segment(val id: Long) {
    val next = AtomicReference<Segment?>(null)
    val cells = AtomicReferenceArray<Any?>(SEGMENT_SIZE)
}

// DO NOT CHANGE THIS CONSTANT
private const val SEGMENT_SIZE = 2

private val POISONED = Any()

