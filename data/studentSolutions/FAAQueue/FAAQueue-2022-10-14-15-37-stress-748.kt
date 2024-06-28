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

    private fun findSegment(segment: Segment, index: Long): Segment {
        var answer = segment
        repeat(index.toInt() - segment.id) {
            if (answer.next == null) {
                val nextSegment = Segment(answer.id + 1)
                answer.next = nextSegment
            }
            answer = answer.next!!
        }
        return answer
    }

    private fun moveTailForward(newTail: Segment) {
        while (newTail.id > tail.value.id) {
            val tailSnapshot = tail.value
            tail.compareAndSet(tailSnapshot, newTail)
        }
    }

    private fun moveHeadForward(newHead: Segment) {
        while (newHead.id > head.value.id) {
            val headSnapshot = head.value
            head.compareAndSet(headSnapshot, newHead)
        }
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val tailSnapshot = tail.value
            val prevEnqIndex = enqIdx.getAndIncrement()
            val segment = findSegment(tailSnapshot, prevEnqIndex / SEGMENT_SIZE)
            moveTailForward(segment)
            if (segment.cas(prevEnqIndex.toInt() % SEGMENT_SIZE, null, element)) {
                return
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
            val headSnapshot = head.value
            val prevDeqIndex = deqIdx.getAndIncrement()
            val segment = findSegment(headSnapshot, prevDeqIndex / SEGMENT_SIZE)
            moveHeadForward(segment)
            if (segment.cas(prevDeqIndex.toInt() % SEGMENT_SIZE, null, null)) {
                continue
            }
            return segment.get(prevDeqIndex.toInt() % SEGMENT_SIZE) as E?
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

private class Segment(val id: Int) {
    var next: Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS
