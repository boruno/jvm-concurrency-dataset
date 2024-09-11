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
        while(true) {
            val curTail = tail.value
            val enqIdx = enqIdx.getAndIncrement()
            if ((enqIdx % SEGMENT_SIZE).toInt() == 0) {
                if (curTail.next != null) {
                    tail.compareAndSet(curTail, curTail.next!!)
                    continue
                }

                val newTail = Segment()
                newTail.elements[0].value = element

                if (curTail.next == null) {
                    curTail.next = newTail
                    tail.compareAndSet(curTail, newTail)
                    return
                }
            } else {
                if (curTail.elements[(enqIdx % SEGMENT_SIZE).toInt()].compareAndSet(null, element))
                    return
            }
        }

    }

    private val DONE = Any()

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            val curHead = head.value
            val deqIdx = deqIdx.getAndIncrement()
            if ((deqIdx % SEGMENT_SIZE).toInt() == 0) {
                if (curHead.next != null) {
                    head.compareAndSet(curHead, curHead.next!!)
                    continue
                } else {
                    return null
                }
            }
            val res = curHead.elements[(deqIdx % SEGMENT_SIZE).toInt()].getAndSet(DONE)
            if (res == null) {
                continue
            }
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
                if ((dIdx / SEGMENT_SIZE).toInt() == 0) {
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

    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

