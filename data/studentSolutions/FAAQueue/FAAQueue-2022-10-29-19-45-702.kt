//package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment(0L)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val currentTail = tail.value
            val index = enqIdx.getAndAdd(1)
            val segment = findSegment(currentTail, index)

            if (currentTail.id != segment.id) {
                if (currentTail.next.compareAndSet(null, segment)) {
                    tail.getAndSet(currentTail.next.value!!)
                } else {
                    continue
                }
            }

            // Trying to replace 'null' value with 'element'.
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

            val currentHead = head.value
            val index = deqIdx.getAndAdd(1)
            val segment = findSegment(currentHead, index)

            if (currentHead.id != segment.id) {
                if (!head.compareAndSet(currentHead, segment))
                    continue
            }

            if (segment.cas((index % SEGMENT_SIZE).toInt(), null, BROKEN))
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

    private fun findSegment(startSegment: Segment, index: Long): Segment {
        val bound = (startSegment.id * SEGMENT_SIZE) + (SEGMENT_SIZE - 1)

        if (index > bound) {
            if (startSegment.next.value != null) {
                return startSegment.next.value!!
            } else {
                return Segment(startSegment.id + 1)
            }
        }

        return startSegment
    }
}

private class Segment(val id: Long) {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }

    override fun toString(): String {
        return "id: $id, next: ${next.value}"
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS
private val BROKEN = Any()