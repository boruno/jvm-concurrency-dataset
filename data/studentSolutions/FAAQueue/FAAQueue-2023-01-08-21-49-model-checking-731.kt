package mpp.faaqueue

import kotlinx.atomicfu.*
import java.util.concurrent.atomic.AtomicReference

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
//        while (true) {
        for (aaa in 1..100000) {
            val curTail = tail.value
            val i = enqIdx.getAndAdd(1)
            val s = findSegment(curTail, i / SEGMENT_SIZE)
            if (curTail != s) {
                tail.compareAndSet(curTail, s)
            }
            if (s.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, element)) {
                throw Exception("not null " + s.elements[(i % SEGMENT_SIZE).toInt()].value.toString())
                return
            }
        }
        throw Exception("enqueue " + enqIdx.value.toString() + " " + deqIdx.value.toString())
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (deqIdx.value >= enqIdx.value) {
                return null
            }
            val curHead = head.value
            val i = deqIdx.getAndAdd(1)
            val s = findSegment(curHead, i / SEGMENT_SIZE)
            if (curHead != s) {
                head.compareAndSet(curHead, s)
            }
//            if (s.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, Any())) {
//                continue
//            }
            while (s.elements[(i % SEGMENT_SIZE).toInt()].value == null) {}
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

    private fun findSegment(start: Segment, id: Long): Segment {
        var elem = start
//        while (true) {
        for (aaa in 1..10000) {
            if (id >= elem.id) {
                return elem
            }
            if (elem.next.value != null || elem.next.compareAndSet(null, Segment(start.id + 1))) {
                elem = elem.next.value!!
            }
        }
        throw Exception("find segment")
    }
}

private class Segment(val id: Long) {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

