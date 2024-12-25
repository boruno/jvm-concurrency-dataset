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
            val curTail = tail.value
            val enqIdx = curTail.enqIdx.getAndIncrement()
            if (enqIdx >= SEGMENT_SIZE) {
                val newTail = Segment(element)
                if (curTail.next.compareAndSet(null, newTail)) {
                    if (tail.compareAndSet(curTail, newTail)) {
                        return
                    }
                } else {
                    val nextTail = curTail.next.value
                    if (nextTail != null) { // What for??
                        tail.compareAndSet(curTail, nextTail)
                    }
                }
            } else {
                if (curTail.cas(enqIdx, null, element)) {
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
            val curHead = head.value
            val deqIdx = curHead.deqIdx.getAndIncrement()
            if (deqIdx >= SEGMENT_SIZE) {
                val nextHead = curHead.next.value ?: return null
                head.compareAndSet(curHead, nextHead)
                continue
            }
            val res = curHead.gas(deqIdx, Any()) ?: continue
            return res as E?
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            while (true) {
                val curHead = head.value
                val deqIdx = curHead.deqIdx.value
                if (deqIdx >= SEGMENT_SIZE) {
                    val nextHead = curHead.next.value ?: return true
                    head.compareAndSet(curHead, nextHead)
                    continue
                } else {
                    return false
                }
            }
        }
}

private class Segment {
    val enqIdx = atomic(0L)
    val deqIdx = atomic(0L)
    val next: AtomicRef<Segment?> = atomic(null)
    private val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    constructor()
    constructor(x: Any?) {
       enqIdx.getAndIncrement()
       gas(0, x)
    }
    private fun get(i: Int) = elements[i].value
    fun cas(i: Long, expect: Any?, update: Any?) = elements[i.toInt()].compareAndSet(expect, update)

    fun gas(i: Long, update: Any?) = elements[i.toInt()].getAndSet(update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

