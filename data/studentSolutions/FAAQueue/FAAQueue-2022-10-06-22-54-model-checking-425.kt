package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private class Segment(val segmentId: Int) {
        abstract class AbstractCell
        object BrokenCell : AbstractCell()
        data class ValueCell<E>(val value: E) : AbstractCell()

        val next: AtomicRef<Segment?> = atomic(null)
        val elements = atomicArrayOfNulls<AbstractCell>(SEGMENT_SIZE)
    }

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
            val i = enqIdx.getAndIncrement()
            val s = findSegment(start = curTail, id = i / SEGMENT_SIZE)
            moveTailAtLeast(s)
            val index = (i % SEGMENT_SIZE).toInt()
            if (s.elements[index].compareAndSet(null, Segment.ValueCell(element))) {
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
            if (isEmpty) {
                return null
            }
            val curHead = head.value
            val i = deqIdx.getAndIncrement()
            val s = findSegment(curHead, i / SEGMENT_SIZE)
            moveHeadAtLeast(s)
            val index = (i % SEGMENT_SIZE).toInt()
            s.elements[index].compareAndSet(null, Segment.BrokenCell)
            when (val value = s.elements[index].value) {
                is Segment.BrokenCell -> continue
                else -> return (value as Segment.ValueCell<E>).value
            }
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() = deqIdx.value <= enqIdx.value

    private fun findSegment(start: Segment, id: Long): Segment {
        require(start.segmentId <= id)
        var cur = start
        while (cur.segmentId < id) {
            if (cur.next.value == null) {
                cur.next.compareAndSet(null, Segment(cur.segmentId + 1))
            }
            cur = cur.next.value!!
        }
        return cur
    }

    private fun moveHeadAtLeast(segment: Segment) {
        while (true) {
            val curHead = head.value
            if (curHead.segmentId >= segment.segmentId) {
                break
            }
            head.compareAndSet(curHead, curHead.next.value!!)
        }
    }

    private fun moveTailAtLeast(segment: Segment) {
        while (true) {
            val curTail = tail.value
            if (curTail.segmentId >= segment.segmentId) {
                break
            }
            tail.compareAndSet(curTail, curTail.next.value!!)
        }
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

