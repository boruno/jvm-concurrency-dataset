package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    object SkipObject

    init {
        val firstNode = Segment()
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    fun findSegment(start: Segment, id: Long): Segment {
        var newStart: Segment = start
        while (newStart.getIndex() <= id) {
            if (newStart.next.value == null) {
                newStart.next.compareAndSet(null, Segment(newStart.getIndex() + 1))
            }
            newStart = newStart.next.value!!
        }
        return newStart
    }

    fun moveTailForward(s: Segment) {
        while (true) {
            val curTail = tail.value
            if (curTail.getIndex() > s.getIndex() || tail.compareAndSet(curTail, s)) {
                return
            }
        }
    }

    fun moveHeadForward(s: Segment) {
        while (true) {
            val curHead = head.value
            if (curHead.getIndex() > s.getIndex() || head.compareAndSet(curHead, s)) {
                return
            }
        }
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val i = enqIdx.getAndIncrement()
            val s: Segment = findSegment(start = curTail, id = i / SEGMENT_SIZE)
            moveTailForward(s)
            if (s.cas((i % SEGMENT_SIZE).toInt(), null, element)) {
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
            val s: Segment = findSegment(start = curHead, id = i / SEGMENT_SIZE)
            moveHeadForward(s)
            if (s.cas((i % SEGMENT_SIZE).toInt(), null, SkipObject)) {
                continue
            }
            @Suppress("UNCHECKED_CAST")
            return s.get((i % SEGMENT_SIZE).toInt()) as E
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean = deqIdx.value >= enqIdx.value
}

class Segment(private var index: Int = 0) {
    val next = atomic<Segment?>(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)
    fun getIndex() = index
    fun setIndex(index: Int) {
        this.index = index
    }

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

