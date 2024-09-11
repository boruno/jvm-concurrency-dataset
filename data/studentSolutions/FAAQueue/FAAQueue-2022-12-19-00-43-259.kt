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
            if (s.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, element)) return
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
            if (s.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, "⊥")) continue
            return s.elements[(i % SEGMENT_SIZE).toInt()].value as E
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
        while (true) {
            val cur_tail = tail.value
            if (cur_tail.id >= s.id) return
            if (tail.compareAndSet(cur_tail, s))
                return
        }
    }

    private fun moveHeadForward(s: Segment) {
        while (true) {
            val cur_head = head.value
            if (cur_head.id >= s.id) return
            if (head.compareAndSet(cur_head, s))
                return
        }
    }

    private fun findSegment(start: Segment, id: Long): Segment {
        var s = start
        while (s.id < id) {
            if (s.next != null)
                s = s.next!!
            else {
                val seg = Segment()
                seg.id = s.id + 1
                if (s.next == null){
                    s.next = seg
                }
            }
        }
        return s
    }
}

private class Segment {
    var id: Long = 0
    var next: Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

