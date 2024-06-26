package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L) // перенес в сегмент, потому что хз как обнулять когда оно тута
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
            val curEnqInd = curTail.enqIdx.getAndIncrement()

            if (curEnqInd >= SEGMENT_SIZE) {
                val newTail = Segment()
                val newEnqInd = newTail.enqIdx.incrementAndGet()
                newTail.cas(newEnqInd, null, element)

                val success = curTail.next.compareAndSet(null, newTail)
                if (success) {
                    tail.compareAndSet(curTail, curTail.next.value!!)
                    return
                }
            } else if (curTail.cas(curEnqInd, null, element)) {
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
            val curDeqInd = curHead.deqIdx.getAndIncrement()

            if (curDeqInd >= SEGMENT_SIZE) {
                val nextHead = curHead.next.value

                if (nextHead == null) {
                    return null
                }

                head.compareAndSet(curHead, nextHead)
            } else {
                val res = curHead.put(curDeqInd,"затычка =)")
                if (res != null) {
                    return res as E?
                }
            }
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            val curHead = head.value

            return curHead.deqIdx.value >= SEGMENT_SIZE ||
                curHead.deqIdx.value >= curHead.enqIdx.value
        }
}

private class Segment {
    val next: AtomicRef<Segment?> = atomic(null)
    val enqIdx = atomic(0)
    val deqIdx = atomic(0)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?): Any? {
        return elements[i].getAndSet(value)
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

