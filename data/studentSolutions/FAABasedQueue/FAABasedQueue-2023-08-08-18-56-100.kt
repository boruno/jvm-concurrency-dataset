//package day2

import java.util.concurrent.atomic.*

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size `Segment`s.
class FAABasedQueue<E> : Queue<E> {
    private val head: AtomicReference<Segment>
    private val tail: AtomicReference<Segment>
    private val enqIdx = AtomicLong(0)
    private val deqIdx = AtomicLong(0)
    init {
        val startingSegment = Segment(0)
        head = AtomicReference(startingSegment)
        tail = AtomicReference(startingSegment)
    }

    override fun enqueue(element: E) {
        while (true) {
            val currentTail = tail.get()
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
            val i = enqIdx.getAndIncrement()

            val segment = findSegment(start = currentTail, id = i / SEGMENT_SIZE)
            moveTailForward(segment)

            // TODO: Atomically install the element into the cell
            // TODO: if the cell is not poisoned.
            if (segment.cells.compareAndSet(i.toInt() % SEGMENT_SIZE, null, element)) {
                return
            }
        }
    }

    private fun findSegment(start: Segment, id: Long): Segment {
        var currentSegment: Segment? = start
        var previous: Segment = start

        while (true) {
            if (currentSegment == null) {
                val newSegment = Segment(previous.id + SEGMENT_SIZE)
                return if (previous.next.compareAndSet(null, newSegment)) {
                    newSegment
                } else {
                    previous.next.get()!!
                }
            }

            if (currentSegment.id == id) {
                return currentSegment
            }
            currentSegment = currentSegment.next.get()
        }
    }

    private fun moveTailForward(segment: Segment) {
        val currentTauil = tail.get()
        if (currentTauil != segment) {
            tail.compareAndSet(currentTauil, segment)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            // Is this queue empty?
            if (deqIdx.get() >= enqIdx.get()) return null

            val currentHead = head.get()
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
            val i = deqIdx.getAndIncrement()

            val segment = findSegment(start = currentHead, id = i / SEGMENT_SIZE)
            moveHeadForward(segment)

            // TODO: Try to retrieve an element if the cell contains an
            // TODO: element, poisoning the cell if it is empty.
            val cellIndex = i.toInt() % SEGMENT_SIZE
            if (segment.cells.compareAndSet(cellIndex, null, POISONED)) {
                continue
            }

            return segment.cells[cellIndex].also { segment.cells.compareAndSet(cellIndex, it, null) } as E
        }
//        TODO("")
    }

    private fun moveHeadForward(segment: Segment) {
        val currentHead = head.get()
        if (currentHead != segment) {
            head.compareAndSet(currentHead, segment)
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
