//package mpp.faaqueue

import kotlinx.atomicfu.*

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

    private fun findSegmentEnqueque(tail: Segment, id: Long): Segment? {
        var count = 0;
        var cur_head = head.value;
        while (cur_head != tail) {
            val cur_head_next = cur_head.next
            if (cur_head_next == null) break
            count++;
            cur_head = cur_head_next;
        }
        if (count < id) {
            return Segment()
        }
        else {
            return null
        }
    }

    private fun moveTailForward(s: Segment?) {
        if (s == null) return
        tail.value.next = s
        tail.value = s
    }

    private fun findSegmentDequeque(head: Segment, id: Long): Segment? {
        var count = 0L;
        var cur_head = head;
        while (count < id) {
            if (cur_head.next == null) return null
            count++;
            cur_head = cur_head.next!!
        }
        return cur_head
    }

    private fun moveHeadForward(s: Segment?) {
        if (s == null) return
        head.value = s
    }

    private fun getElement(i: Long): E {
        var id = i / SEGMENT_SIZE
        var cur_head = head.value
        for (j in 0 until id) {
            cur_head = cur_head.next!!
        }
        return cur_head.elements[(i % SEGMENT_SIZE).toInt()].value as E
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val cur_tail = tail.value
            val i = enqIdx.getAndAdd(1)
            val s = findSegmentEnqueque(cur_tail, i / SEGMENT_SIZE)
            moveTailForward(s)
            if (s != null) {
                if (s.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, element)) {
                    return
                }
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
            if (isEmpty) return null
            var cur_head = head.value
            var i = deqIdx.getAndAdd(1)
            var s = findSegmentDequeque(cur_head, i / SEGMENT_SIZE)
//            moveHeadForward(s)

            if (s != null) {
                var res = s.elements[(i % SEGMENT_SIZE).toInt()].value
                if (s.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, "Broken element")) {
                    continue
                }
            }
            if (s != null) {
                return s.elements[(i % SEGMENT_SIZE).toInt()].value as E?
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
}

private class Segment {
    var next: Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

