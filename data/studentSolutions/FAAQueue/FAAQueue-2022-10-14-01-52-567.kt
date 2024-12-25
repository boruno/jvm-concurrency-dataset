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

    private fun findSegment(startingNode: Segment, index: Long): Segment {
        var curNode = startingNode
        while (curNode.index < index) {
            if (curNode.next != null) {
                curNode = curNode.next!!
            } else {
                curNode = Segment(curNode.index + 1)
            }
        }
        return curNode
    }
//private fun findSegment(startingNode: Segment, index: Long): Segment {
//        var curNode = startingNode
//        while (curNode.index < index) {
//            if (curNode.next.value != null) {
//                curNode = curNode.next.value!!
//            } else {
//                if (curNode.next.compareAndSet(null, Segment(curNode.index + 1)))
//                    continue
//                curNode = curNode.next.value!!
//            }
//        }
//        return curNode
//    }

    private fun moveTailForward(targetNode: Segment) {
        while (true) {
            val curNode = tail.value
            if (curNode.index < targetNode.index) {
                if (tail.compareAndSet(curNode, targetNode))
                    break
            } else
                break
        }
    }

    private fun moveHeadForward(targetNode: Segment) {
        while (true) {
            val curNode = head.value
            if (curNode.index < targetNode.index) {
                if (head.compareAndSet(curNode, targetNode))
                    break
            } else
                break

        }
    }

    /**
     * Adds the specified element [x] to the queue.
     */
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
            if (isEmpty)
                return null
            val curHead = head.value
            val index = deqIdx.getAndAdd(1)
            val segment = findSegment(curHead, index / SEGMENT_SIZE)
            moveHeadForward(segment)
            if (segment.cas((index % SEGMENT_SIZE).toInt(), null, Dummy()))
                continue
            return segment.get((index % SEGMENT_SIZE).toInt()) as E?
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.value >= enqIdx.value
        }

    private class Dummy()

}

private class Segment(val index: Long) {
//    var next: AtomicRef<Segment?> = atomic(null)
    var next: Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE: Int = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

