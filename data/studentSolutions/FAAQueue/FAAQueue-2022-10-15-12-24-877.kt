//package mpp.faaqueue

import kotlinx.atomicfu.*
import java.lang.reflect.Constructor

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment()
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     */

    private fun findSegment(start : Segment, id : Long) : Segment {
        while (true) {
            var cur = start
            while (cur.next.value != null && cur.id != id) {
                cur = cur.next.value!!
            }
            if (cur.id != id) {
                val new_seg = Segment()
                new_seg.id = id
                if (cur.next.compareAndSet(null, new_seg)) {
                    return new_seg
                }
            }
            else {
                return cur
            }
        }
    }

    private fun moveHeadForward(cur : Segment) {
        while (true) {
            val previous = head.value
            if (head.value.id > cur.id) {
                return
            }
            if (head.compareAndSet(previous, cur)) {
                return
            }
        }
    }

    private fun  moveTailForward(cur : Segment) {
        while (true) {
            val previous = tail.value
            if (tail.value.id > cur.id) {
                return
            }
            if (tail.compareAndSet(previous, cur)) {
                return
            }
        }
    }



    fun enqueue(element: E) {
        while (true) {
            val cur_tail = tail.value
            val i = enqIdx.addAndGet(1)
            val s = findSegment(start = cur_tail, id = i / SEGMENT_SIZE)
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
            if (deqIdx.value <= enqIdx.value) {
                return null
            }
            val cur_head = head.value
            val i = deqIdx.addAndGet(1)
            val s = findSegment(start = cur_head, id = i / SEGMENT_SIZE)
            moveHeadForward(s)
            if (s.cas((i % SEGMENT_SIZE).toInt(), null, my_null())) {
                continue
            }
            return s.get((i % SEGMENT_SIZE).toInt()) as E?
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return enqIdx.value == deqIdx.value
        }
}

private class Segment {
    var id : Long = 0
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    /*private*/ fun get(i: Int) = elements[i].value
    /*private*/ fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    /*private*/ fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

private class my_null {
}