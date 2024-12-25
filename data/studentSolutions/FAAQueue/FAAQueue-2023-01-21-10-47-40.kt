//package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<T> {
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
    fun enqueue(element: T) {
        while (true) {
            val tail: Segment = this.tail.value
            val en = enqIdx.getAndIncrement()
            if (en < SEGMENT_SIZE) {
                if (tail.cas(en.toInt(),null, element)) {
                    return
                }
            } else {
                val newT = Segment()
                newT.put(0, element)
                if (tail.next.compareAndSet(null, newT)) {
                    this.tail.compareAndSet(tail, newT)
                    return
                }
                this.tail.compareAndSet(tail, tail.next.value!!)
            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): T? {
        while (true) {
            val head = head.value
            val deq = deqIdx.getAndIncrement()
            if (deq >= SEGMENT_SIZE) {
                val headN = head.next.value ?: return null
                this.head.compareAndSet(head, headN)
                continue
            }
            val res = head.put(deq.toInt(), DONE)
            return res as T?
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            while (true) {
                val head = this.head.value
                if (deqIdx.value >= SEGMENT_SIZE || deqIdx.value >= 0) {
                    val headN = head.next.value ?: return true
                    this.head.compareAndSet(head, headN)
                    continue
                } else {
                    return false
                }
            }
        }
}

private class Segment {

    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].getAndSet(value)
    }
}

private val DONE = Any()
const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

