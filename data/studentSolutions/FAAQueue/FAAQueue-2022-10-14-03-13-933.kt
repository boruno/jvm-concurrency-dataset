//package mpp.faaqueue

import kotlinx.atomicfu.*

@Suppress("UNCHECKED_CAST")
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

    private fun findSegment(start: Segment, id: Long): Segment {
        var temp = start
        for (i in 1..id) {
            val curValue = temp
            if (curValue.next.value == null) {
                val newSegment = Segment(curValue.id + 1)
                curValue.next.compareAndSet(null, newSegment)
            }
            temp = start.next.value!!
        }
        return temp
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while(true) {
            val curTail = tail.value
            val i = enqIdx.getAndIncrement()
            val s = findSegment(start = curTail, id = i / SEGMENT_SIZE)
            moveTailForward(s)
            if (s.cas((i % SEGMENT_SIZE).toInt(), null, element)) {
                return
            }
        }
    }

    private fun moveTailForward(s: Segment) {
        while (s.id > tail.value.id) {
            val curTailValue = tail.value
            tail.compareAndSet(curTailValue, s)
        }
    }

    private fun moveHeadForward(s: Segment) {
        while (s.id > tail.value.id) {
            val curHeadValue = head.value
            head.compareAndSet(curHeadValue, s)
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while(true) {
            if (deqIdx.value >= enqIdx.value) return null
            val curHead = head
            val curHeadValue = curHead.value
            val i = deqIdx.getAndIncrement()
            val s = findSegment(start = curHeadValue, id = i / SEGMENT_SIZE)
            moveHeadForward(s)
            if (s.cas((i % SEGMENT_SIZE).toInt(), null, 0)) continue
            return head.value.get((i % SEGMENT_SIZE).toInt()) as E
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() = enqIdx.value == deqIdx.value
}

private class Segment (val id: Long) {
    val next: AtomicRef<Segment?> = atomic(null)
    private val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

