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
            val cur_tail = tail.value
            val i = enqIdx.getAndAdd(1)
            val s: Segment = findSegment(current = cur_tail, idx = i)
            if (!moveTailForward(cur_tail, s)) continue
            if (s.cas((i % SEGMENT_SIZE).toInt(), null, element)) {
                return
            }
        }
    }

    private fun findSegment(current: Segment, idx: Long): Segment {
        var current_ = current
        while (current_.firstId + SEGMENT_SIZE - 1 < idx) {
            current_.next.compareAndSet(null, Segment(current_.firstId + SEGMENT_SIZE))
            current_ = current_.next.value!!
        }
        return current_
    }

    private fun moveTailForward(cur_tail: Segment, s: Segment): Boolean {
        return tail.compareAndSet(cur_tail, s)
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (deqIdx.value >= enqIdx.value) return null;
            val cur_head = head.value
            val i = deqIdx.getAndAdd(1)
            val s = findSegment(current = cur_head, idx = i)
            if (!moveHeadForward(cur_head, s)) continue
            if (s.cas((i % SEGMENT_SIZE).toInt(), null, "hui")) continue
            // s.get(i)
            return s.get(i.toInt()) as E?
        }
    }

    private fun moveHeadForward(cur_head: Segment, s: Segment): Boolean {
        return head.compareAndSet(cur_head, s)
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.value <= enqIdx.value;
        }
}

private class Segment {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)
    var firstId: Int

    constructor(firstId: Int) {
        this.firstId = firstId
    }

    public fun get(i: Int) = elements[i].value
    public fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    public fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

