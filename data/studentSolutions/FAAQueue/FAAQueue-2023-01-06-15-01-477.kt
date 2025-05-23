//package mpp.faaqueue

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
        while (true) {
            val curTail = tail.value
            val idx = enqIdx.getAndIncrement()
            val segmentIdx = idx / SEGMENT_SIZE
            val needSegment = findSegment(curTail, segmentIdx)
            if (needSegment != curTail) {
                if (tail.value.next.compareAndSet(null, needSegment)) {
                    tail.compareAndSet(curTail, needSegment)
                }
            }

            if (tail.value.elements[(idx % SEGMENT_SIZE).toInt()].compareAndSet(null, element)) {
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
            if (enqIdx.value <= deqIdx.value) {
                return null
            }

            val curHead = head.value
            val idx = deqIdx.getAndIncrement()
            val segmentIdx = idx / SEGMENT_SIZE
            val needSegment = findSegment(curHead, segmentIdx)
            if (needSegment != curHead) {
                if (head.value.next.compareAndSet(null, needSegment)) {
                    head.compareAndSet(curHead, needSegment)
                }
            }

            if (curHead.elements[(idx % SEGMENT_SIZE).toInt()].compareAndSet(null, BROKEN_MARKER)) {
                continue
            } else {
                return curHead.elements[(idx % SEGMENT_SIZE).toInt()].value as E?
            }
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() = (deqIdx.value <= enqIdx.value)


    private fun findSegment(start: Segment, idx: Long): Segment {
        var tmpTail: Segment? = start
        while (tmpTail != null && tmpTail.idx < idx) {
            tmpTail = tmpTail.next.value
        }

        return tmpTail ?: Segment(idx)
    }
}

private class Segment(val idx: Long = 0L) {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS
private const val BROKEN_MARKER = ""

