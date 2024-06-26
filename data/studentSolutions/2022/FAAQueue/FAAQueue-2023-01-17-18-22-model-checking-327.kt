package mpp.faaqueue

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
            val tailSnapshot = tail.value
            val enqIdx = tailSnapshot.enqIdx.getAndIncrement()
            if (enqIdx >= SEGMENT_SIZE) {
                val newTail = Segment(element)
                if (tailSnapshot.next.compareAndSet(null, newTail)) {
                    tail.compareAndSet(tailSnapshot, newTail)
                    return
                }
            } else {
                if (tailSnapshot.cas(enqIdx, null, element)) {
                    return
                }
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
            var headSnapshot = head.value
            val deqIdx = headSnapshot.deqIdx.getAndIncrement()
            if (deqIdx >= SEGMENT_SIZE) {
                val nextHead = headSnapshot.next.value
                if (nextHead == null) {
                    return null
                }
                head.compareAndSet(headSnapshot, nextHead)
                headSnapshot = nextHead // continue instead?
            }
            val element = headSnapshot.gas(deqIdx, Segment.BROKEN)
            return element as? E
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            val headSnapshot = head.value
            val tailSnapshot = tail.value
            return headSnapshot == tailSnapshot && headSnapshot.isEmpty
        }
}

private class Segment {
    companion object {
        val BROKEN = Any()
    }

    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)
    val enqIdx: AtomicInt
    val deqIdx = atomic(0)

    constructor() {
        enqIdx = atomic(0)
    }

    constructor(element: Any?) {
        enqIdx = atomic(1)
        elements[0].value = element
    }

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
    fun gas(i: Int, update: Any?) = elements[i].getAndSet(update)
    val isEmpty: Boolean
        get() = enqIdx.value <= deqIdx.value
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

