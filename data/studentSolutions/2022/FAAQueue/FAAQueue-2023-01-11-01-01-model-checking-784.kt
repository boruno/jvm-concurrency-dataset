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
//    fun enqueue(element: E) {
//        while (true) {
//            val currTail = tail.value
//            val i = enqIdx.getAndIncrement() // FAA(&enqIdx, +1)
//
//            // findSegment(start = currTail, id = i / SEGMENT_SIZE)
//            val id = i / SEGMENT_SIZE
//            if (id >= currTail.currentNumberBlock) {
//                val newSegment = Segment()
//                newSegment.currentNumberBlock = id.toInt()
//
//                //currTail.next = newSegment
//                if (currTail.next.compareAndSet(null, newSegment))
//                    tail.compareAndSet(currTail, newSegment)
//            } else {
//                if (currTail.cas((i % SEGMENT_SIZE).toInt(), null, element)) return
//            }
//        }
//    }

    private fun moveTailForward(s: Segment) {
        while (true) {
            val cur_tail = tail.value
            if (cur_tail.currentNumberBlock >= s.currentNumberBlock) return
            if (tail.compareAndSet(cur_tail, s))
                return
        }
    }

    fun enqueue(element: E) {
        while (true) {
            val cur_tail = tail.value
            val i = enqIdx.getAndIncrement()
            val s = findSegment(cur_tail, i / SEGMENT_SIZE)
            moveTailForward(s)
            if (s.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, element))
                return
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (isEmpty) return null
            val cur_head = head.value
            val i = deqIdx.getAndIncrement()
            val s = findSegment(cur_head, i / SEGMENT_SIZE)
            moveHeadForward(s)
            if (s.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, Any()))
                continue
            return s.elements[(i % SEGMENT_SIZE).toInt()].value as E
        }

    }

    private fun moveHeadForward(s: Segment) {
        while (true) {
            val cur_head = head.value
            if (cur_head.currentNumberBlock >= s.currentNumberBlock) return
            if (head.compareAndSet(cur_head, s))
                return
        }
    }

    private fun findSegment(start: Segment, id: Long): Segment {
        var s = start
        while (s.currentNumberBlock < id) {
            if (s.next.value != null)
                s = s.next.value!!
            else {
                val segNew = Segment()
                segNew.currentNumberBlock = s.currentNumberBlock + 1
                s.next.compareAndSet(null, segNew)
            }
        }
        return s
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            TODO("implement me")
        }
}

private class Segment {
    var currentNumberBlock = -1
    val next = atomic<Segment?>(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

