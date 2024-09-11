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
            val curTail: Segment = tail.value
            val index: Long = enqIdx.getAndAdd(1)
            val segment: Segment = findSegment(curTail, index / SEGMENT_SIZE)
            moveTailForward(segment)
            if (segment.cas((index % SEGMENT_SIZE).toInt(), null, element)) {
                return
            }
        }
    }

    private fun moveTailForward(segment: Segment) {
        while (true) {
            val curTail: Segment = tail.value
            if (curTail.index < segment.index) {
                if (tail.compareAndSet(curTail, segment)) {
                    return
                }
            } else {
                return
            }
        }
    }

    private fun moveHeadForward(segment: Segment) {
        while (true) {
            val curHead: Segment = head.value
            if (curHead.index < segment.index) {
                if (head.compareAndSet(curHead, segment)) {
                    return
                }
            } else {
                return
            }
        }
    }

    private fun findSegment(start: Segment, index: Long): Segment {
        var cur: Segment = start
        while (cur.index != index) {
            val next: Segment = cur.next ?: Segment(index)
            cur.next = next
            cur = next
        }
        return cur
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
            val curHead: Segment = head.value
            val index: Long = deqIdx.getAndAdd(1)
            val segment: Segment = findSegment(curHead, index / SEGMENT_SIZE)
            moveHeadForward(segment)
            val indexInSegment: Int = (index % SEGMENT_SIZE).toInt()
            if (segment.cas(indexInSegment, null, BROKEN)) {
                continue
            }
            return segment.get(indexInSegment) as E?
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.value == enqIdx.value
        }
}

private class Segment(val index: Long) {
    var next: Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

private enum class Broken {BROKEN}

private val BROKEN: Broken = Broken.BROKEN
