package day2

import day1.*
import java.util.concurrent.atomic.*

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size `Segment`s.
class FAABasedQueue<E> : Queue<E> {
    private val head = AtomicReference(Segment(0))
    private val tail = AtomicReference(head.get())

    override fun enqueue(element: E) {
        while (true) {
            val tail = tail.get()
            val enqIndex = tail.enqIdx.getAndIncrement()
            if (enqIndex < SEGMENT_SIZE && tail.cells.compareAndSet(enqIndex.toInt(), null, element)) {
                return
            }

            val segment = Segment(element)
            if (tail.next.compareAndSet(null, segment)) {
                this.tail.compareAndSet(tail, segment)
                return
            }

            val next = tail.next.get()
            check(next != null) { "Impossible state" }
            this.tail.compareAndSet(tail, next)
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val head = head.get()
            val deqIndex = head.deqIdx.getAndIncrement()

            if (deqIndex >= SEGMENT_SIZE) {
                val nextHead = head.next.get() ?: return null
                this.head.compareAndSet(head, nextHead)
                continue
            }

            val res = head.cells.getAndSet(deqIndex.toInt(), POISONED) ?: continue
            @Suppress("UNCHECKED_CAST")
            return res as E?
        }
    }
}

private class Segment(x: Any?) {
    val enqIdx = AtomicLong(0) // index for the next enqueue operation
    val deqIdx = AtomicLong(0) // index for the next dequeue operation
    val next = AtomicReference<Segment?>(null)
    val cells = AtomicReferenceArray<Any?>(SEGMENT_SIZE)

    // each next new segment should be constructed with an element
    init {
        enqIdx.incrementAndGet()
        cells.getAndSet(0, x)
    }
}

// DO NOT CHANGE THIS CONSTANT
private const val SEGMENT_SIZE = 2

private val POISONED = Any()
