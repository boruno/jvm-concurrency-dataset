//package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    // перенес в сегмент для обнуления простого
    //private val enqIdx = atomic(0)
    //private val deqIdx = atomic(0)

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
            val curEnqInx = curTail.enqIdx.getAndIncrement()

            if (curEnqInx >= SEGMENT_SIZE) {
                val newSegment = Segment()
                newSegment.put(0, element)
                if (tail.value.cas(0, curTail, newSegment)) {
                    return
                }
            }
            else if (curTail.elements[curEnqInx].compareAndSet(null, element)) {
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
            val curDeqIdx = curHead.deqIdx.getAndIncrement()

            if (curDeqIdx >= SEGMENT_SIZE) {
                val newHead = curHead.next

                if (newHead == null) {
                    return null
                }

                if (head.compareAndSet(curHead, newHead)) {
                    return newHead.elements[curDeqIdx].value as E?
                }
            } else {
                val answer = curHead.elements[curDeqIdx].getAndSet("Перпендикуляр =)")
                if (answer != null) {
                    return answer as E?
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

            return curHead.deqIdx.value >= curHead.enqIdx.value || curHead.deqIdx.value >= SEGMENT_SIZE
        }
}

private class Segment {
    var next: Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)
    val enqIdx = atomic(0)
    val deqIdx = atomic(0)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

