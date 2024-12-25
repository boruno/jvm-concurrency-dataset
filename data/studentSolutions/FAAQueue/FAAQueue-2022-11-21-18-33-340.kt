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
            var curEnqInx = curTail.enqIdx.getAndIncrement()

            if (curEnqInx >= SEGMENT_SIZE) {
                val newSegment = Segment()
                curEnqInx = newSegment.enqIdx.getAndIncrement()
                newSegment.put(curEnqInx, element)
                val success = tail.value.next.compareAndSet(null, newSegment)
                if (!success) continue

                tail.compareAndSet(curTail, newSegment)
                if (success) {
                    return
                }
            }
            else if (curTail.cas(curEnqInx, null, element)) {
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
                val newHead = curHead.next.value
                if (newHead == null) {
                    return null
                }

                head.compareAndSet(curHead, newHead)
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
            while (true) {
                if (head.value.isEmpty) {
                    if (head.value.next.value == null) {
                        return true
                    }
                    head.value = head.value.next.value!!
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
    val enqIdx = atomic(0)
    val deqIdx = atomic(0)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }

    val isEmpty: Boolean get() = deqIdx.value >= enqIdx.value || deqIdx.value >= SEGMENT_SIZE
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

