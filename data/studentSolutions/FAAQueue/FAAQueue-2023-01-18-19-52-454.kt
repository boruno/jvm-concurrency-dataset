//package mpp.faaqueue

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


    private fun findSegment(start: Segment, id: Long): Segment {
        var now = start
        while (now.id < id) {
            if (now.next.value == null) {
                now.next.compareAndSet(null, Segment(start.id + 1))
            }
            now = now.next.value!!
        }
        return start
    }

    private fun moveTailForward(s: Segment) {
        while (true) {
            val curTail = tail.value
            if (curTail.id < s.id) {
                if (tail.compareAndSet(curTail, s)) {
                    return
                }
            } else {
                break
            }
        }
    }

    private fun moveHeadForward(s: Segment) {
        while (true) {
            val curHead = head.value
            if (curHead.id < s.id) {
                if (head.compareAndSet(curHead, s)) {
                    return
                }
            } else {
                break
            }
        }
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val index = enqIdx.getAndIncrement()
            val s = findSegment(curTail, index / SEGMENT_SIZE)
            moveTailForward(s)
            if (s.cas((index % SEGMENT_SIZE).toInt(), null, element)) {
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
            if (isEmpty) return null
            val curHead = head.value
            val index = deqIdx.getAndIncrement()
            val s = findSegment(curHead, index / SEGMENT_SIZE)
            moveHeadForward(s)
            if (s.cas((index % SEGMENT_SIZE).toInt(), null, InvalidCell())) {
                continue
            }
            val result = s.get((index % SEGMENT_SIZE).toInt()) as E?
            s.put((index % SEGMENT_SIZE).toInt(), TakenCell())
            return result
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.value > enqIdx.value
        }

    private class Segment(id_: Long) {
        val id = id_
        val next: AtomicRef<Segment?> = atomic(null)
        val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

        fun get(i: Int) = elements[i].value
        fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
        fun put(i: Int, value: Any?) {
            elements[i].value = value
        }
    }

    private class InvalidCell {}
    private class TakenCell {}
}


const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

