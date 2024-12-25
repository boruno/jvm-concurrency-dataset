//package mpp.faaqueue

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
            val curTail = tail.value
            val enqIdx = enqIdx.getAndIncrement()
            if (enqIdx >= SEGMENT_SIZE) {
                val seg = Segment()
                seg.put(0,element)
                if (curTail.next!!.cas(0, null, seg)) {
                    tail.compareAndSet(curTail, seg)
                    return
                } else {
                    tail.compareAndSet(curTail, curTail.next!!)
                }
            } else {
                if (curTail.elements[enqIdx.toInt()].compareAndSet(null, element)) {
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
            val head = this.head.value
            val curDeqIdx = deqIdx.getAndIncrement()
            if (curDeqIdx >= SEGMENT_SIZE) {
                val headNext = head.next ?: return null
                this.head.compareAndSet(head, headNext)
                continue
            }
            val res = head.elements[curDeqIdx.toInt()].getAndSet(Any()) ?: continue
            return res as E?
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            while (true) {
                val head = head.value
                if (head.elements.size == 0) {
                    val headNext = head.next ?: return true
                    this.head.compareAndSet(head, headNext)
                } else {
                    return false
                }
            }
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

