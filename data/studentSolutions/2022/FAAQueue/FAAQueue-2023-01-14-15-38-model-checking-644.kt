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
     * Adds the specified element [element] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val idx = enqIdx.getAndIncrement()
            val seg = findSegment(curTail, idx / SEGMENT_SIZE)
            if (seg.id > idx / SEGMENT_SIZE) {
                tail.compareAndSet(curTail, seg)
            }
            if (seg.cas((idx % SEGMENT_SIZE).toInt(), null, element)) {
                return
            }
        }
    }

    private fun findSegment(curTail: Segment, idx: Long): Segment {
        var cur = curTail
        while (cur.id < idx) {
            val next = cur.next.value
            if (next == null) {
                val newSeg = Segment(idx)
                if (cur.next.compareAndSet(null, newSeg)) {
                    return newSeg
                }
            } else {
                cur = next
            }
        }
        return cur
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
            val curHead = head.value
            val idx = deqIdx.getAndIncrement()
            val seg = findSegment(curHead, idx / SEGMENT_SIZE)
            if (seg.id > idx / SEGMENT_SIZE) {
                head.compareAndSet(curHead, seg)
            }
            if (seg.cas((idx % SEGMENT_SIZE).toInt(), null, BAD)) {
                continue
            }

            return seg.get((idx % SEGMENT_SIZE).toInt()) as E?
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            TODO("implement me")
        }
}

private class Segment(val id : Long) {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

private val BAD = Any()
const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

