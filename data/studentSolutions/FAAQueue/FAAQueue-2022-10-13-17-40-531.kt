package mpp.faaqueue

import kotlinx.atomicfu.*
import kotlin.reflect.jvm.internal.impl.load.kotlin.JvmType

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
            val i = enqIdx.getAndAdd(1)
            val s = findSegment(curTail, i)
            moveTailForward(s)

            if (s.cas((i - s.startInd).toInt(), null, element))
                return
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
            val i = enqIdx.getAndAdd(1)
            val s = findSegment(curHead, i)
            moveHeadForward(s)

            if (!s.cas((i - s.startInd).toInt(), null, Any()))
                return s.get((i - s.startInd).toInt()) as E?
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() = deqIdx.value <= enqIdx.value

    private fun findSegment(s: Segment, i: Long) : Segment {
        var cur = s
        while (i >= cur.startInd + SEGMENT_SIZE) {
            while (cur.next.value == null)
                cur.next.compareAndSet(null, Segment(cur.startInd + SEGMENT_SIZE))
            cur = cur.next.value!!
        }
        return cur
    }

    private fun moveTailForward(s: Segment) {
        val curTail = tail.value
        if (s.startInd > curTail.startInd)
            tail.compareAndSet(curTail, s)
    }

    private fun moveHeadForward(s: Segment) {
        val curHead = head.value
        if (s.startInd > curHead.startInd)
            head.compareAndSet(curHead, s)
    }
}

private class Segment(val startInd: Long) {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) : Boolean = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

