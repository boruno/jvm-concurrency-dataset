//package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue

    // Don't need because it's superior version
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
            val ourTail = tail.value
            val ourEnqIdx = ourTail.enqIdx.getAndIncrement()
            if (ourEnqIdx >= SEGMENT_SIZE) {
                if (ourTail.next.compareAndSet(null, Segment(element))) {
                    this.tail.compareAndSet(ourTail, ourTail.next.value!!)
                    return
                } else {
                    this.tail.compareAndSet(ourTail, ourTail.next.value!!)
                }
            } else {
                if (ourTail.cas(ourEnqIdx, null, element))
                    return
                // throw IllegalStateException("Такое вообще возможно????") --  возможно
            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            val head = this.head.value
            val deqIdx = head.deqIdx.getAndIncrement()
            if (deqIdx >= SEGMENT_SIZE) {
                val headNext = head.next.value ?: return null
                this.head.compareAndSet(head, headNext)
                continue
            }
            return head.get(deqIdx) as  E?: continue
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return tail.value.isEmpty
        }
}

private class Segment {
    val next: AtomicRef<Segment?> = atomic(null)
    val enqIdx = atomic(0)
    val deqIdx = atomic(0)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    constructor() {}

    constructor(x: Any?) {
        elements[enqIdx.getAndIncrement()].getAndSet(x)
    }

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
    val isEmpty: Boolean get() = deqIdx.value >= enqIdx.value || deqIdx.value >= SEGMENT_SIZE
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

