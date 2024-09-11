package mpp.faaqueue

import kotlinx.atomicfu.*
import java.lang.IllegalStateException

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)
    private val BAD_ELEM = "~~~~~~"

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
            val curTail = tail.value;
            val i = enqIdx.getAndIncrement();
            val s = findSegment(curTail, i / SEGMENT_SIZE);
            if (tail.compareAndSet(curTail, s) && tail.value.cas((i % SEGMENT_SIZE).toInt(), null, element)) {
                return
            }
        }
    }

    private fun findSegment(curTail: Segment, id: Long): Segment {
        var cur : Segment? = curTail;
        while (true) {
            if (cur != null) {
                if (cur.index == id) {
                    return cur;
                }
                if (cur.next == null) {
                    cur.next = Segment(cur.index + 1);
                }
                cur = cur.next;
            } else {
                throw IllegalStateException("cur null")
            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    @Suppress("UNCHECKED_CAST")
    fun dequeue(): E? {
        while (true) {
            if (deqIdx.value >= enqIdx.value) {
                return null
            }
            val curHead = head.value;
            val i = deqIdx.getAndIncrement();
            val s = findSegment(curHead, i / SEGMENT_SIZE);
            if (head.compareAndSet(curHead, s) && head.value.cas((i % SEGMENT_SIZE).toInt(), null, BAD_ELEM)) {
                continue
            }
            val res = head.value.get((i % SEGMENT_SIZE).toInt())
            if (res == BAD_ELEM) {
                return null
            }
            return res as E;
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return enqIdx.getAndAdd(0) == deqIdx.getAndAdd(0);
        }
}

private class Segment(val index: Long) {
    var next: Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

