//package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<T> {
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
    fun enqueue(x: T) {
        while (true) {
            val tail = this.tail.value
            val endIdx = tail.enqIdx.getAndIncrement()
            if (endIdx >= SEGMENT_SIZE) {
                if (tail.next.compareAndSet(null, Segment(x))) {
                    this.tail.compareAndSet(tail, tail.next.value!!)
                    return
                } else {
                    this.tail.compareAndSet(tail, tail.next.value!!)
                }
            } else {
                if (tail.elements[endIdx].compareAndSet(null, x))
                    return
                // throw IllegalStateException("Такое вообще возможно????") --  возможно
            }
        }
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): T? {
        while (true) {
            val head = this.head.value
            val deqIdx = head.deqIdx.getAndIncrement()
            if (deqIdx >= SEGMENT_SIZE) {
                val headNext = head.next.value ?: return null
                this.head.compareAndSet(head, headNext)
                continue
            }
            return head.elements[deqIdx].value as T ?: continue
        }
    }

    /**
     * Returns `true` if this queue is empty;
     * `false` otherwise.
     */
    val isEmpty: Boolean get() {
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

