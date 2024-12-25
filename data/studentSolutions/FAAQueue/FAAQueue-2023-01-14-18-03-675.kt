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

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val tailVal = tail.value
            val idx = enqIdx.getAndIncrement()
            val segment = findSegment(tailVal, idx / SEGMENT_SIZE)

            val tailValue = tail.value
            if (tailValue.index >= segment.index || tail.compareAndSet(tailValue, segment)) {
                break
            }
            if (segment.cas((idx % SEGMENT_SIZE).toInt(), null, element)) {
                break
            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (deqIdx.value >= enqIdx.value) {
                return null
            }
            val curHead: Segment = head.value
            val index: Long = deqIdx.getAndAdd(1)
            val segment = findSegment(curHead, index / SEGMENT_SIZE)

            while (true) {
                val curHead = head.value
                if (curHead.index >= segment.index) break
                if (head.compareAndSet(curHead, segment)) break
            }
            val indexInSegment = (index % SEGMENT_SIZE).toInt()
            if (segment.cas(indexInSegment, null, false)) {
                continue
            }
            @Suppress("UNCHECKED_CAST")
            return segment.get(indexInSegment) as E
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.value == enqIdx.value
        }

    private fun findSegment(start: Segment, index: Long): Segment {
        var cur = start
        while (cur.index < index) {

            cur.next.compareAndSet(null, Segment(cur.index + 1))

            cur = cur.next.value!!
        }
        return cur
    }
}

private class Segment(val index: Long) {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

