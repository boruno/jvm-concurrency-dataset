//package day2

import java.util.concurrent.atomic.*

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size `Segment`s.
class FAABasedQueue<E> : Queue<E> {
    private val enqIdx = AtomicLong(0)
    private val deqIdx = AtomicLong(0)

    private val head: AtomicReference<Segment>
    private val tail: AtomicReference<Segment>

    init {
        val segment = Segment(0)
        head = AtomicReference(segment)
        tail = AtomicReference(segment)
    }

    private fun findSegment(segment: Segment, number: Long): Segment {
        if (segment.id == number) return segment
        var cur = segment
        while (true) {
            if (cur.id == number) return cur
            val next = cur.next.get() ?: break
            cur = next
        }

        // we have to add one more segment
        val node = Segment(number)
        return if (cur.next.compareAndSet(null, node)) {
            node
        } else {
            findSegment(segment, number)
        }
    }

    override fun enqueue(element: E) {
        while (true) {
            var curTail = tail.get()
            val idx = enqIdx.getAndIncrement()
            val s = findSegment(curTail, idx / SEGMENT_SIZE)
            curTail = tail.get()
            if (curTail.id < s.id) {
                tail.compareAndSet(curTail, s)
            }
            if (s.cells.compareAndSet((idx % SEGMENT_SIZE).toInt(), null, element)) return
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (queueIsEmpty()) return null // queue is empty
            var curHead = head.get()
            val idx = deqIdx.getAndIncrement()
            val s = findSegment(curHead, idx / SEGMENT_SIZE)
            curHead = head.get()
            if (curHead.id < s.id) {
                head.compareAndSet(curHead, s)
            }
            if (s.cells.compareAndSet((idx % SEGMENT_SIZE).toInt(), null, POISONED)) continue
            return s.cells.getAndSet((idx % SEGMENT_SIZE).toInt(), null) as E
        }
    }

    private fun queueIsEmpty(): Boolean {
        while (true) {
            val curEnq = enqIdx.get()
            val curDeq = deqIdx.get()
            val newCurEnq = enqIdx.get()
            if (newCurEnq != curEnq) continue
            return curEnq <= curDeq
        }
    }
}

private class Segment(val id: Long) {
    val next = AtomicReference<Segment?>(null)
    val cells = AtomicReferenceArray<Any?>(SEGMENT_SIZE)
}

// DO NOT CHANGE THIS CONSTANT
private const val SEGMENT_SIZE = 2

// TODO: poison cells with this value.
private val POISONED = Any()
