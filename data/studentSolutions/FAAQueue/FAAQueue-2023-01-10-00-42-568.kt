//package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue

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
            val currentTail = this.tail.value
            val idx = currentTail.enqIdx.getAndIncrement()
            if (idx < SEGMENT_SIZE) {
                if (currentTail.cas(idx, null, element)) {
                    return
                }
            } else {
                if (currentTail == this.tail.value) {
                    val currentTailNext = currentTail.next.value
                    if (currentTailNext != null) {
                        this.tail.compareAndSet(currentTail, currentTailNext)
                    } else {
                        val newElement = Segment().apply { put(0, element) }
                        if (currentTail.next.compareAndSet(null, newElement)) {
                            this.tail.compareAndSet(currentTail, newElement)
                            return
                        }
                    }
                }
            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            val currentHead = this.head.value
            if (currentHead.deqIdx.value >= currentHead.enqIdx.value && currentHead.next.value == null) {
                return null
            }
            val idx = currentHead.deqIdx.getAndIncrement()
            if (idx < SEGMENT_SIZE) {
                val element: E = currentHead.elements[idx].getAndSet(Any()) as E
                if (element != null) {
                    return element
                }
            } else {
                if (currentHead.next.value == null) {
                    return null
                } else {
                    this.head.compareAndSet(currentHead, currentHead.next.value!!)
                }
            }
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return false
        }
}

private class Segment {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)
    val enqIdx = atomic(0)
    val deqIdx = atomic(0)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

