package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment(0)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val ourTail = tail.value
            val ourEnqIdx = enqIdx.getAndIncrement()
            if (ourTail.elements[(ourEnqIdx % SEGMENT_SIZE).toInt()].compareAndSet(null, element))
                return
            else {
                if (ourTail.next == null) {
                    tail.compareAndSet(ourTail, Segment(ourEnqIdx % SEGMENT_SIZE))
                } else {
                    tail.compareAndSet(ourTail, ourTail.next!!)
                }
            }
//            if (ourTail.elements[(ourEnqIdx % SEGMENT_SIZE).toInt()].value != null) {
//
//            } else {
//                if (ourTail.elements[(ourEnqIdx % SEGMENT_SIZE).toInt()].compareAndSet(null, element))
//                    return
//            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (deqIdx.value <= enqIdx.value) return null
            val ourHead = head.value
            val ourHeadNext = head.value.next
            val ourDeqIdx = deqIdx.getAndIncrement()
            if (ourHead.id < ourDeqIdx / SEGMENT_SIZE) {
                if (ourHeadNext == null) return null
                head.compareAndSet(ourHead, ourHeadNext)
            }
            return ourHead.elements[ourDeqIdx.toInt() % SEGMENT_SIZE].getAndSet(Int.MAX_VALUE) as E? ?: continue
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.value <= enqIdx.value
        }
}

private class Segment(val id: Long) {
    var next: Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

