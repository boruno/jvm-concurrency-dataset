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
            val tail: Segment = tail.value
            val enqIdx: Long = enqIdx.getAndIncrement()
            if (enqIdx >= SEGMENT_SIZE) {
                val newTail = Segment(element)
                if (tail.next == null) {
                    tail.next = newTail
                    this.tail.compareAndSet(tail, newTail)
                    return
                }
                this.tail.compareAndSet(tail, tail.next!!)
            } else if (tail.elements[enqIdx.toInt()].compareAndSet(null, element)) {
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
            val curHead = head.value
            val deqIdx = deqIdx.getAndIncrement()
            if (deqIdx >= SEGMENT_SIZE) {
                val nextHead = curHead.next ?: return null
                head.compareAndSet(curHead, nextHead)
                continue
            }
            val res = curHead.elements[deqIdx.toInt()].getAndSet(Any()) ?: continue
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
                val eIdx = enqIdx.value
                val dIdx = deqIdx.value
                if (dIdx >= SEGMENT_SIZE) {
                    val curHeadNext = curHead.next
                    if (curHeadNext == null) {
                        return true
                    } else {
                        head.compareAndSet(curHead, curHeadNext)
                    }
                } else {
                    return dIdx >= eIdx
                }
            }
        }
}

private class Segment {
    var next: Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    constructor() {
    }
    constructor(element: Any?) {
        elements[0].getAndSet(element)
    }

    fun findSegment(enqIdx: Int, element: Any?, tail: Segment): Segment? {
        if (enqIdx >= SEGMENT_SIZE) {
            return Segment(element)
        } else {
            return null
        }
    }
    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

