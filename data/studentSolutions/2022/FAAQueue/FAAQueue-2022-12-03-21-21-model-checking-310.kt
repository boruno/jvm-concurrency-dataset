package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment(0)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val i = enqIdx.getAndIncrement()
            var s = curTail
            while (s.index < i / SEGMENT_SIZE) {
                if (s.next.value == null) {
                    s.next.compareAndSet(null, Segment(s.index + 1))
                }
                s = s.next.value as Segment
            }
            if (s.index > curTail.index)
                tail.compareAndSet(curTail, s)
            if (s.cas((i % SEGMENT_SIZE) as Int, null, element))
                return
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (deqIdx.value <= enqIdx.value)
                return null
            val curHead = head.value
            val i = deqIdx.getAndIncrement()
            var s = curHead
            while (s.index < i / SEGMENT_SIZE) {
                if (s.next.value == null) {
                    s.next.compareAndSet(null, Segment(s.index + 1))
                }
                s = s.next.value as Segment
            }
            if (s.index > curHead.index)
                head.compareAndSet(curHead, s)
            if (s.cas((i % SEGMENT_SIZE) as Int, null, KEK))
                continue
            return s.get(i as Int) as E?
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            val curHeadNext = head.value.next.value
            return curHeadNext == null
        }
}

class Segment(val index: Int) {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

private object KEK: Any()
const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

