//package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    init {
        val firstNode = Segment(0)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [element] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val i = enqIdx.getAndAdd(1)
            val s = findSegment(curTail, i / SEGMENT_SIZE)

            moveTailForward(s)
            if (s.cas(i % SEGMENT_SIZE, null, element)) {
                return
            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (deqIdx.value >= enqIdx.value) {
                return null
            }

            val curHead = head.value
            val i = deqIdx.getAndAdd(1)
            val s = findSegment(curHead, i / SEGMENT_SIZE)

            moveHeadForward(s)
            if (s.cas(i % SEGMENT_SIZE, null, Broken())) {
                continue
            }

            @Suppress("UNCHECKED_CAST")
            return s.get(i % SEGMENT_SIZE) as E?
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.value >= enqIdx.value
        }

    private fun findSegment(start: Segment, id: Int): Segment {
        var cur = start
        for (i in 0 until start.id - id) {
            val next = cur.next
            cur = if (next.value != null) {
                next.value!!
            } else {
                val newSegment = Segment(cur.id + 1)
                if (cur.next.compareAndSet(null, newSegment)) {
                    newSegment
                } else {
                    cur.next.value!!
                }
            }
        }
        return cur
    }

    private fun moveHeadForward(s: Segment) {
        while (true) {
            val curHead = head.value
            val curNext = curHead.next.value

            if (curHead.id >= s.id) {
                return
            }
            head.compareAndSet(curHead, curNext!!)
        }
    }

    private fun moveTailForward(s: Segment) {
        while (true) {
            val curTail = tail.value
            val curNext = curTail.next.value

            if (curTail.id >= s.id) {
                return
            }
            head.compareAndSet(curTail, curNext!!)
        }
    }
}

private class Segment(val id: Int) {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

private class Broken

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

