//package day2

import day1.*
import kotlinx.atomicfu.*

class FAABasedQueue<E> : Queue<E> {
    private val head: AtomicRef<Segment>
    private val tail: AtomicRef<Segment>
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    init {
        val segment = Segment(0)
        head = atomic(segment)
        tail = atomic(segment)
    }

    override fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val i = enqIdx.getAndIncrement()
            val segment = findSegment(curTail, i / BLOCK_SIZE)
            moveTailForward(segment)
            if (segment.array[i % BLOCK_SIZE].compareAndSet(null, element)) {
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (isEmpty()) return null
            val curHead = head.value
            val i = deqIdx.getAndIncrement()
            val segment = findSegment(curHead, i / BLOCK_SIZE)
            moveHeadForward(segment)
            if (!segment.array[i % BLOCK_SIZE].compareAndSet(null, POISONED)) {
                return segment.array[i % BLOCK_SIZE].value as E
            }
        }
    }

    private fun moveTailForward(segment: Segment) {
        while (true) {
            val curTail = tail.value
            if (curTail.id >= segment.id) return
            if (tail.compareAndSet(curTail, segment)) {
                return
            }
        }
    }

    private fun moveHeadForward(segment: Segment) {
        while (true) {
            val curTail = head.value
            if (curTail.id >= segment.id) return
            if (head.compareAndSet(curTail, segment)) {
                return
            }
        }
    }

    private fun findSegment(start: Segment, id: Int): Segment {
        var s: Segment? = start
        var last: Segment = start
        while (s != null) {
            if (s.id == id) return s
            s = s.next.value
            if (s != null) last = s
        }
        for (i in (last.id + 1)..id) {
            val newSegment = Segment(i)
            last.next.value = newSegment
            last = newSegment
        }
        return last
    }

    private fun isEmpty(): Boolean {
        while (true) {
            val deq = deqIdx.value
            val enq = enqIdx.value
            if (deq != deqIdx.value) continue
            return deq >= enq
        }
    }

    private class Segment(val id: Int) {
        val array = atomicArrayOfNulls<Any?>(BLOCK_SIZE)
        val next: AtomicRef<Segment?> = atomic(null)
    }
}

private val BLOCK_SIZE = 1
private val POISONED = Any()
