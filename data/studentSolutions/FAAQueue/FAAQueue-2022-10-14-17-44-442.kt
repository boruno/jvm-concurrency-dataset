//package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    private val DONE = Any()


    init {
        val firstNode = Segment(0)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        val enqIdx = this.enqIdx.getAndIncrement()
        var tail = this.tail.value
        if (tail.id < enqIdx / faaqueue.SEGMENT_SIZE) {
            if (tail.next == null) tail.next = Segment(tail.id + 1)
            tail = tail.next!!
            this.tail.value = tail
        }
        val i = (enqIdx % faaqueue.SEGMENT_SIZE).toInt()
        tail.elements[i].value = element
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        if (enqIdx.value == deqIdx.value) return null
        val deqIdx = this.deqIdx.getAndIncrement()
        var head = this.head.value
        if (head.id < deqIdx / faaqueue.SEGMENT_SIZE) {
            head = head.next!!
            this.head.value = head
        }
        val i = (deqIdx % faaqueue.SEGMENT_SIZE).toInt()
        return head.elements[i].value as E
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.value >= enqIdx.value
        }

    private fun moveTailForward(s: Segment) {

    }
    private fun moveHeadForward(s: Segment) {

    }

    private fun findSegment(start: Segment, id: Long): Segment {
//        if (start.id < id) {
//            if (start.next == null) start.next = Segment(start.id + 1)
//            tail = start.next!!
//            this.tail.value = tail
//        }
        return Segment(1)
    }
}

private class Segment(val id: Long) {
    var next: Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

