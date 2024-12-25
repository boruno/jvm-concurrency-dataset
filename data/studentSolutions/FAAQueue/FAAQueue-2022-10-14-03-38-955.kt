//package mpp.faaqueue

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

    /**
     * Adds the specified element [x] to the queue.
     */

//    fun enqueue(element: E) {
//        while (true) {
//            val cur_tail = tail.value
//            val i = enqIdx.getAndIncrement()
//            val s = findSegment(cur_tail, i / SEGMENT_SIZE)
//            moveTailForward(s)
//            if (s.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, element))
//                return
//        }
//    }

    fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val index = enqIdx.getAndAdd(1)
            val segment = findSegment(curTail, index / SEGMENT_SIZE)
            moveTailForward(segment)
            if (segment.cas((index % SEGMENT_SIZE).toInt(), null, element))
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
            if (s.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, broken()))
                continue
            return s.elements[(i % SEGMENT_SIZE).toInt()].value as E
        }

    }

    class broken() {}

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            val enq = enqIdx.value
            val deq = deqIdx.value
            return deq < enq
        }

//    private fun moveTailForward(s: Segment) {
//        while (true) {
//            val cur_tail = tail.value
//            if (cur_tail.id >= s.id) return
//            if (tail.compareAndSet(cur_tail, s))
//                return
//        }
//    }
    private fun moveTailForward(targetNode: Segment) {
        while (true) {
            val curNode = tail.value
            if (curNode.id < targetNode.id) {
                if (tail.compareAndSet(curNode, targetNode))
                    break
            } else
                break
        }
}

//    private fun moveHeadForward(s: Segment) {
//        while (true) {
//            val cur_head = head.value
//            if (cur_head.id >= s.id) return
//            if (tail.compareAndSet(cur_head, s))
//                return
//        }
//    }
private fun moveHeadForward(targetNode: Segment) {
    while (true) {
        val curNode = head.value
        if (curNode.id < targetNode.id) {
            if (head.compareAndSet(curNode, targetNode))
                break
        } else
            break

    }
}
//    private fun findSegment(start: Segment, id: Long): Segment {
//        var s = start
//        while (s.id < id) {
//            if (s.next.value != null)
//                s = s.next.value!!
//            else {
//                s.next.compareAndSet(null, Segment(s.id + 1))
//            }
//        }
//        return s
//    }
private fun findSegment(startingNode: Segment, index: Long): Segment {
    var curNode = startingNode
    while (curNode.id < index) {
        if (curNode.next.value != null) {
            curNode = curNode.next.value!!
        } else {
            if (curNode.next.compareAndSet(null, Segment(curNode.id + 1)))
                continue
            curNode = curNode.next.value!!
        }
    }
    return curNode
}

}


private class Segment(val id: Long) {

    val next = atomic<Segment?>(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    private fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

