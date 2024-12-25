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
            val currentSegmentBound = (currentTail.id * SEGMENT_SIZE) + (SEGMENT_SIZE - 1)

            if (index > currentSegmentBound) {
                if (currentTail != tail.value)
                    continue

                val nextSegment = currentTail.next.value

                if (nextSegment == null) {
                    val newSegment = Segment(index / SEGMENT_SIZE)
                    newSegment.put((index % SEGMENT_SIZE).toInt(), element)

                    if (currentTail.next.compareAndSet(null, newSegment)) {
                        tail.compareAndSet(currentTail, newSegment)
                        return
                    }
                } else {
                    tail.compareAndSet(currentTail, nextSegment)
                }

                continue
            }

            if (currentTail.cas((index % SEGMENT_SIZE).toInt(), null, element))
                return
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            val currentHead = head.value

            if (isEmpty)
                break

            val index = deqIdx.getAndAdd(1)
            val currentSegmentBound = (currentHead.id * SEGMENT_SIZE) + (SEGMENT_SIZE - 1)

            if (index > currentSegmentBound) {
                val nextSegment = currentHead.next.value ?: break
                head.compareAndSet(currentHead, nextSegment)
                continue
            }

            if (currentHead.cas((index % SEGMENT_SIZE).toInt(), null, BROKEN))
                continue

            val element = currentHead.get((index % SEGMENT_SIZE).toInt()) as E?

            if (element == BROKEN)
                return element
        }

        return null
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.value >= enqIdx.value && head.value.next.value == null
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
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS
private val BROKEN = Any()