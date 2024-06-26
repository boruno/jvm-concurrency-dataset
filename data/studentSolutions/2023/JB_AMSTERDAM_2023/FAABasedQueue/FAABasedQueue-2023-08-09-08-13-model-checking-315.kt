package day2

import day1.Queue
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray

class FAABasedQueue<E> : Queue<E> {
    private val head = AtomicReference<Segment>()
    private val tail = AtomicReference<Segment>()
    private val enqIdx = AtomicLong(0)
    private val deqIdx = AtomicLong(0)

    override fun enqueue(element: E) {
        while (true) {
            val curTail = tail
            val i = enqIdx.getAndIncrement()
            val s = findSegment(start = curTail, id = i / SEGMENT_SIZE)
            moveTailForward(s)
            if (s.cells.compareAndSet((i % SEGMENT_SIZE).toInt(), null, element)) {
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            val deq1 = deqIdx.get()
            val enq = enqIdx.get()
            val deq2 = deqIdx.get()
            if (deq1 != deq2) {
                continue
            }
            // Is this queue empty?
            if (deq1 >= enq) {
                return null
            }
            val curHead = head
            val i = deqIdx.getAndIncrement()
            val s = findSegment(start = curHead, id = i / SEGMENT_SIZE)
            moveHeadForward(s)
            if (s.cells.compareAndSet((i % SEGMENT_SIZE).toInt(), null, POISONED)) {
                continue
            }
            return s.cells.getAndSet((i % SEGMENT_SIZE).toInt(), null) as E
        }
    }

    private fun findSegment(start: AtomicReference<Segment>, id: Long): Segment {
        var curr = start.get()
        while (true) {
            if (curr.id == id) {
                return curr
            }
            curr = curr.next.get() ?: break
        }
        var currId = curr.id
        while (currId != id) {
            val segment = Segment(currId + SEGMENT_SIZE)
            if (curr.next.compareAndSet(null, segment)) {
                curr = segment
                currId = segment.id
            } else {
                curr = curr.next.get()
                currId = curr.id
            }
        }
        return curr
    }

    private fun moveTailForward(s: Segment) {
        val curTail = tail.get()
        if (curTail.id < s.id) {
            if (!tail.compareAndSet(curTail, s)) {
                return
            }
        }
    }

    private fun moveHeadForward(s: Segment) {
        var curr = head.get()
        while (curr != s) {
            val next = curr.next.get()
            if (!head.compareAndSet(curr, next)) {
                return
            }
            curr = next
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
