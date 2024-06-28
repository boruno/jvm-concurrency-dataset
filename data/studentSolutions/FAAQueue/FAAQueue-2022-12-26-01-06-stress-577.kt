package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)
    private val brokenCell = '⊥'

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
            val i = enqIdx.getAndAdd(1)
            val s = findSegment(curTail, i / SEGMENT_SIZE)
            moveTailForward(s)
            if (s.cas((i % SEGMENT_SIZE).toInt(), null, element)) {
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
            if (s.cas((i % SEGMENT_SIZE).toInt(), null, brokenCell)) {
                continue
            }
            return s.get((i % SEGMENT_SIZE).toInt()) as? E
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.value >= enqIdx.value
        }

    private fun findSegment(start: Segment, id: Long): Segment {
        while (true) {
            var curS = start
            while (curS.id != id) {
                val next = curS.next.value
                if (next == null) break
                else curS = next
            }

            if (curS.id == id) {
                return curS
            } else {
                val newSegment = Segment(id)
                if (curS.next.compareAndSet(null, newSegment)) {
                    return newSegment
                }
            }
        }
    }

    private fun moveTailForward(s: Segment) {
        while (true) {
            val curTail = tail.value
            if (curTail.id < s.id) {
                if (tail.compareAndSet(curTail, s)) return
            } else return
        }
    }

    private fun moveHeadForward(s: Segment) {
        while (true) {
            val curHead = head.value
            if (curHead.id < s.id) {
                if (head.compareAndSet(curHead, s)) return
            } else return
        }
    }
}

private class Segment(val id: Long) {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

