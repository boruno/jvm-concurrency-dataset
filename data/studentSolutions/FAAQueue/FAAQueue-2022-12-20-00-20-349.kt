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
            val curTail = tail.value
            val idx = enqIdx.getAndAdd(1)
            val curSegment = findSegment(curTail, idx / SEGMENT_SIZE)
            moveTail(curSegment)
            if (curSegment.cas((idx % SEGMENT_SIZE).toInt(), null, element)) {
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
            val idx = deqIdx.getAndAdd(1)
            val curSegment = findSegment(curHead, idx / SEGMENT_SIZE)
            moveHead(curSegment)
            if (curSegment.cas((idx % SEGMENT_SIZE).toInt(), null, false)) continue
            return curSegment.get((idx % SEGMENT_SIZE).toInt()) as? E
        }
    }

    private fun findSegment(ss: Segment, id: Long): Segment {
        var s: Segment = ss
        var next: Segment?
        for (i in s.id until id) {
            next = s.next.value

            if (next == null) {
                val newSegment = Segment(i + 1)
                s.next.compareAndSet(null, newSegment)
            }
            s = next!!
        }

        return s
    }

    private fun moveTail(s: Segment) {
        while (true){
            val curTail = tail.value
            if (curTail.id < s.id) {
                if (tail.compareAndSet(curTail, s)) {
                    return
                }
            } else {
                return
            }
        }
    }

    private fun moveHead(s: Segment) {
        while (true){
            val curHead = head.value
            if (curHead.id < s.id) {
                if (head.compareAndSet(curHead, s)) {
                    break
                }
            } else {
                return
            }
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.value >= enqIdx.value
        }
}

private class Segment(val id: Long) {
    val next = atomic<Segment?>(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

