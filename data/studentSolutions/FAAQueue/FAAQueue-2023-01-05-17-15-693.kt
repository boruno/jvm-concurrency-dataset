//package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    private val broken = "broken"

    init {
        val firstNode = Segment(0)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    private fun findSegment(start: Segment, id: Long): Segment? {
        var st = start
        while(st.id < id) {
            if(st.next.value == null) {
                val newNode = Segment(st.id + 1)
                st.next.compareAndSet(null, newNode)
            }
            st = st.next.value!!
        }
        if(st.id > id)
            return null
        return st
    }

    private fun moveTailForward(s: Segment) {
        while(true) {
            val curTail = tail.value
            if(curTail.id < s.id) {
                if(tail.compareAndSet(curTail, s)) {
                    return
                }
            } else {
                return
            }
        }
    }

    private fun moveHeadForward(s: Segment) {
        while(true) {
            val curHead = head.value
            if(curHead.id < s.id) {
                if (head.compareAndSet(curHead, s)) {
                    return
                }
            } else {
                return
            }
        }
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while(true) {
            val curTail = tail
            val i = enqIdx.getAndAdd(1)
            val s = findSegment(curTail.value, i / SEGMENT_SIZE) ?: continue
            moveTailForward(s)
            if(s.cas((i % SEGMENT_SIZE).toInt(), null, element)) {
                return
            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while(true) {
            if (isEmpty)
                return null
            val curHead = head
            val i = deqIdx.getAndAdd(1)
            val s = findSegment(curHead.value, i / SEGMENT_SIZE) ?: continue
            moveHeadForward(s)
            if(s.cas((i % SEGMENT_SIZE).toInt(), null, broken))
                continue
            return s.get((i % SEGMENT_SIZE).toInt()) as E
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return enqIdx.value <= deqIdx.value
        }
}

private class Segment(val id: Long) {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

