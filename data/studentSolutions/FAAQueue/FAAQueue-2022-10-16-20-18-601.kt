//package mpp.faaqueue

import kotlinx.atomicfu.*

const val BROKEN = -1

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
            val index = enqIdx.getAndAdd(1)

            if (index >= SEGMENT_SIZE) {
                val newSegment = Segment()
                val currentSegement = tail.value
                newSegment.put(0, element)

                if (tail.compareAndSet(currentSegement, newSegment)) {
                    currentSegement.next = newSegment
                    enqIdx.value = 0L
                }
            } else if (tail.value.cas(index.toInt(), null, element)) {
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
            val index = deqIdx.getAndAdd(1)

            if (index >= SEGMENT_SIZE) {
                val next = head.value.next ?: return null

                if (head.compareAndSet(head.value, next)) {
                    deqIdx.value = 0
                    continue
                }
            }

            // Make a swap for BROKEN
            val result = head.value.get(index.toInt()) ?: continue

            return result as E?
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

private class Segment {
    var next: Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

