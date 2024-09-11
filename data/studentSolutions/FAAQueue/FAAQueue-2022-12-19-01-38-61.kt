package mpp.faaqueue

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

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while(true){
            val cur_tail = tail.value
            val i = enqIdx.getAndIncrement()
            val s = findSegment(cur_tail, i / SEGMENT_SIZE)
            moveTailForward(s)
            if (s.cas((i % SEGMENT_SIZE).toInt(), null, element)) return
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while(true) {
            if (isEmpty) return null
            val cur_head = head.value
            val i = deqIdx.getAndIncrement()
            val s = findSegment(cur_head, i / SEGMENT_SIZE)
            moveHeadForward(s)
            if (s.cas((i % SEGMENT_SIZE).toInt(),null, "⊥")) continue
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

    private fun moveTailForward(s: Segment) {
        val cur_tail = tail.value
        if (cur_tail.id >= s.id) return
        tail.compareAndSet(cur_tail, s)
    }

    private fun moveHeadForward(s: Segment) {
        val cur_head = head.value
        if (cur_head.id >= s.id) return
        head.compareAndSet(cur_head, s)
    }

    private fun findSegment(start: Segment, id: Long): Segment {
        var s = start
        val seg = Segment()
        seg.id = s.id + 1
        while (s.id < id) {
            if (s.next.value == null) s.next.compareAndSet(null,seg) else s = s.next.value!!;
        }
        return s
    }
}

private class Segment {
    var id: Long = 0
    val next = atomic<Segment?>(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

