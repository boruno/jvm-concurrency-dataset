package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)
    private val segIdx = atomic(0L)

    init {
        val firstNode = Segment(segIdx.getAndAdd(1))
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
            if (s.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, element)) {
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
            if (isEmpty)
                return null
            val curHead = head.value
            val i = deqIdx.getAndAdd(1)
            val s = findSegment(curHead, i / SEGMENT_SIZE)
            moveHeadForward(s)
            if (s.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, BREAK_SIGNAL)) {
                continue
            }
            val res = s.elements[(i % SEGMENT_SIZE).toInt()].value
            try {
                return  res as E
            } catch (e: Exception) {
                throw RuntimeException(res.toString())
            }
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
        var cur = start
        while (cur.id < id) {
            if (cur.next == null) {
                cur.next = Segment(segIdx.getAndAdd(1))
            }
            cur = cur.next!!
        }
        return cur
    }

    private fun moveTailForward(cur: Segment) {
        if (tail.value.id < cur.id) {
            tail.value = cur
        }
    }

    private fun moveHeadForward(cur: Segment) {
        if (head.value.id < cur.id) {
            head.value = cur
        }
    }
}

private class Segment(val id: Long) {
    var next: Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS
const val BREAK_SIGNAL = "uytbyuybtubuibinguyngyufnynyctyctyctyctycghtycty"
