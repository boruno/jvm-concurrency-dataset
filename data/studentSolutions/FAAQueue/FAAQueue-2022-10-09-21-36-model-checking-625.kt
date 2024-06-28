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
    fun enqueue(x: E) {
        while (true) {
            val curTail = tail.value
            val enqIdx = curTail.enqIdx.getAndIncrement()
            if (enqIdx >= SEGMENT_SIZE) {
                val newTail = Segment(x)
                if (curTail.next.compareAndSet(null, newTail)) {
                    tail.compareAndSet(curTail, newTail)
                    return
                }
                tail.compareAndSet(curTail, curTail.next.value!!)
            } else if (curTail.cas(enqIdx, null, x)) {
                return
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
            val head = head.value
            if (head.isEmpty) {
                val headNext = head.next.value ?: return null
                this.head.compareAndSet(head, headNext)
            } else {
                val deqIdx = head.deqIdx.getAndIncrement()
                if (deqIdx >= SEGMENT_SIZE) {
                    continue
                }
                val e = head.elements[deqIdx].getAndSet(DONE) ?: continue
                return e as E?
            }
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            while (true) {
                val head = head.value
                if (head.isEmpty) {
                    val headNext = head.next.value ?: return true
                    this.head.compareAndSet(head, headNext)
                } else {
                    return false
                }
            }
        }
}

private class Segment {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    val enqIdx = atomic(0)
    val deqIdx = atomic(0)

    val isEmpty: Boolean
        get() = deqIdx.value >= enqIdx.value || deqIdx.value >= SEGMENT_SIZE

    constructor()

    constructor(x: Any?) {
        enqIdx.value = 1
        put(0, x)
    }

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

private val DONE = Object() // Marker for the "DONE" slot state; to avoid memory leaks

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

