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


    private fun findSegment(start: Segment, id: Long): Segment {
        if (start.id < id) {
            if (start.next.value != null)
                return start.next.value!!
            else {
                while (true) {
                    if (start.next.compareAndSet(null, Segment(start.id + 1)))
                        return start
                }
            }
        }
        return start
    }

    private fun moveTail(s: Segment) {
        while (true) {
            val cur_tail = tail.value
            if (cur_tail.id >= s.id) return
            if (tail.compareAndSet(cur_tail, s)) return
        }
    }

    private fun moveHead(s: Segment) {
        while (true) {
            val cur_head = head.value
            if (cur_head.id >= s.id) return
            if (head.compareAndSet(cur_head, s)) return
        }
    }

    /**
     * Adds the specified element [x] to the queue.
     */

    fun enqueue(element: E) {
        while (true) {
            val cur_tail = tail.value
            val i = enqIdx.getAndIncrement()
            val s = findSegment(cur_tail, i / SEGMENT_SIZE)
            moveTail(s)
            if (s.cas((i % SEGMENT_SIZE).toInt(), null, element)) return
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (deqIdx.value >= enqIdx.value) return null
            val cur_head = head.value
            val i = deqIdx.getAndIncrement()
            val s = findSegment(cur_head, i / SEGMENT_SIZE)
            moveHead(s)
            if (s.cas((i % SEGMENT_SIZE).toInt(), null, Any())) continue
            return s.get((i % SEGMENT_SIZE).toInt()) as E
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


private class Segment(val id: Long) {
    val next = atomic<Segment?>(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS