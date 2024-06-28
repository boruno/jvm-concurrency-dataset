package mpp.faaqueue

import kotlinx.atomicfu.*

@Suppress("UNCHECKED_CAST")
class FAAQueue<E> {
    private val head: AtomicRef<Segment>
    private val tail: AtomicRef<Segment>
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment(0)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    fun enqueue(element: E) {
        while (true) {
            moveTailForward(findSegment(tail.value, enqIdx.getAndAdd(1) / SEGMENT_SIZE))
            if (findSegment(tail.value, enqIdx.getAndAdd(1) / SEGMENT_SIZE).cas((enqIdx.getAndAdd(1) % SEGMENT_SIZE).toInt(), null, element)) {
                return
            }
        }
    }

    private fun moveTailForward(segment: Segment) {
        while (true) {
            if (tail.value.index < segment.index) {
                if (tail.compareAndSet(tail.value, segment)) {
                    return
                }
            } else {
                return
            }
        }
    }

    private fun moveHeadForward(segment: Segment) {
        while (true) {
            if (head.value.index < segment.index) {
                if (head.compareAndSet(head.value, segment)) {
                    return
                }
            } else {
                return
            }
        }
    }

    private fun findSegment(start: Segment, index: Long): Segment {
        var cur: Segment = start
        while (cur.index < index) {
            cur.next.compareAndSet(null, Segment(cur.index + 1))
            cur = cur.next.value!!
        }
        return cur
    }

    fun dequeue(): E? {
        while (true) {
            if (deqIdx.value >= enqIdx.value) {
                return null
            }
            val index: Long = deqIdx.getAndAdd(1)
            val segment: Segment = findSegment(head.value, index / SEGMENT_SIZE)
            moveHeadForward(segment)
            val indexInSegment: Int = (index % SEGMENT_SIZE).toInt()
            if (segment.cas(indexInSegment, null, BROKEN)) {
                continue
            }
            return segment.get(indexInSegment) as E?
        }
    }

    val isEmpty: Boolean
        get() {
            return deqIdx.value == enqIdx.value
        }
}

private class Segment(val index: Long) {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)
    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
}

const val SEGMENT_SIZE = 2
private enum class Broken {BROKEN}

private val BROKEN: Broken = Broken.BROKEN