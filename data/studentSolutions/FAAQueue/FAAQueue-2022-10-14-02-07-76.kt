//package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {

    private val BROKEN = Any()

    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment()
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    private fun moveTail(s: Segment) {
        while (true) {
            val curTail = tail.value
            if (curTail.id >= s.id)
                break
            tail.compareAndSet(curTail, s)
        }
    }

    private fun moveHead(s: Segment) {
        while (true) {
            val curHead = head.value
            if (curHead.id >= s.id)
                break
            head.compareAndSet(curHead, s)
        }
    }

    private fun findSegment(start: Segment, id: Long): Segment {
        var result = start
        while (result.id < id) {
            val curTail = result.next.value
            if (curTail == null)
                result.next.compareAndSet(null, Segment(result.id + 1))
            else
                result = curTail
        }
        return result
    }

    /**
    * Adds the specified element [x] to the queue.
    */
    fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val i = enqIdx.getAndIncrement()
            val s = findSegment(curTail, i / SEGMENT_SIZE)
            moveTail(s)
            if (s.cas((i % SEGMENT_SIZE).toInt(), null, element))
                return
        }
    }

    /**
    * Retrieves the first element from the queue and returns it;
    * returns `null` if the queue is empty.
    */
    fun dequeue(): E? {
        while (true) {
            val curEnq = enqIdx.value
            val curDeq = deqIdx.value
            if (curEnq <= curDeq) return null
            val curHead = head.value
            val i = deqIdx.getAndIncrement()
            val s = findSegment(curHead, i / SEGMENT_SIZE)
            moveHead(s)
            if (s.cas((i % SEGMENT_SIZE).toInt(), null, BROKEN))
                continue
            return s.get((i % SEGMENT_SIZE).toInt()) as E
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() = (enqIdx.value <= deqIdx.value)
}


private class Segment(val id: Long = 0) {
    val next = atomic<Segment?>(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

