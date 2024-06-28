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
        while (true) {
            val currentTail = tail
            val i = enqIdx.getAndIncrement()
            val segment = findSegment(currentTail.value, i / SEGMENT_SIZE) ?: continue
            if (!moveTailForward(segment)) {continue}
            if (segment.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, element)) {
                return
            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (isEmpty) {
                return null
            }
            val currentHead = head
            val i = deqIdx.getAndIncrement()
            val segment = findSegment(currentHead.value, i / SEGMENT_SIZE) ?: continue
            if (!moveHeadForward(segment)) {continue}
            if (segment.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, 'T')) {
                continue
            }
            return segment.elements[(i % SEGMENT_SIZE).toInt()].value as E?
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return (deqIdx.value >= enqIdx.value)
        }

    private fun findSegment(start: Segment, id: Long): Segment? {
        var currentSegment = start
        if (currentSegment.id == id) {
            return start
        }
        if (currentSegment.id > id) {
            return null
        }
        while (true) {
            if (currentSegment.next == null) {
                val newSegment = Segment()
                newSegment.id = currentSegment.id + 1L
                currentSegment.next = newSegment
                if (newSegment.id == id) {
                    return newSegment
                } else {
                    currentSegment = newSegment
                }
            } else {
                currentSegment = currentSegment.next!!
                if (currentSegment.id == id) {
                    return currentSegment
                }
            }
        }
    }

    private fun moveTailForward(newTail: Segment): Boolean {
        while (true) {
            val currentTail = tail
            if (newTail.id < currentTail.value.id) {
                return false
            }
            if (tail.compareAndSet(currentTail.value, newTail)) {
                enqIdx.incrementAndGet()
                return true
            }

        }

    }

    private fun moveHeadForward(newHead: Segment): Boolean {
        while (true) {
            val currentHead = head
            if (newHead.id < currentHead.value.id) {
                return false
            }
            if (head.compareAndSet(currentHead.value, newHead)) {
                deqIdx.incrementAndGet()
                return true
            }
        }
    }
}

private class Segment {
    var next: Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)
    var id = 0L

    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

