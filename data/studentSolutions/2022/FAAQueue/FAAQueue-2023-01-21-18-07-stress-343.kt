package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

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
            val enqInd = enqIdx.getAndIncrement()
            if (enqInd >= SEGMENT_SIZE) {
                val nextTail = Segment(element)
                if (curTail.next.compareAndSet(null, nextTail)) {
                    tail.compareAndSet(curTail, nextTail)
                    return
                } else {
                    val newTail = curTail.next.value ?: continue
                    this.tail.compareAndSet(curTail, newTail)
                }
            } else {
                if (curTail.cas(enqInd, null, element)) {
                    return
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
            val curHead = head.value
            val deqInd = deqIdx.getAndIncrement()
            if (deqInd >= SEGMENT_SIZE) {
                val headNext = curHead.next.value ?: return null
                this.head.compareAndSet(curHead, headNext)
                continue
            }
            val r = curHead.cas(deqInd, Any(), DONE);
            val res = curHead.elements[deqInd].getAndSet(DONE) ?: continue
            return r as E?
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
    //val enqIdx = atomic(0)
    //val deqIdx = atomic(0)

    constructor()

    constructor(element: Any?) {
        this.cas(0,null, element)
    }

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

private val DONE = Any()
const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

