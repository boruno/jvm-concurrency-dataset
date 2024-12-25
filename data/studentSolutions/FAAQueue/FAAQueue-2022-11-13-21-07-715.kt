//package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment<Option<E>>> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment<Option<E>>> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment<Option<E>>()
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
    * Adds the specified element to the queue.
    */
    fun enqueue(element: E) {
        while (true) {
            var currentTail = tail.value
            val index = enqIdx.getAndIncrement()
            val segment = currentTail.findSegment(index / SEGMENT_SIZE)

            while (true) {
                if (currentTail.id >= segment.id || tail.compareAndSet(currentTail, segment)) {
                    break
                }
                currentTail = tail.value
            }

            val indexInSegment = (index % SEGMENT_SIZE).toInt()

            if (segment.cas(indexInSegment, null, Some(element))) {
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
            val deqIndex = deqIdx.value
            val enqIndex = enqIdx.value
            if (deqIndex >= enqIndex) {
                return null
            }
            var currentHead = head.value
            val index = deqIdx.getAndIncrement()
            val segment = currentHead.findSegment(index / SEGMENT_SIZE)

            while (true) {
                if (currentHead.id >= segment.id || head.compareAndSet(currentHead, segment)) {
                    break
                }
                currentHead = head.value
            }

            val indexInSegment = (index % SEGMENT_SIZE).toInt()
            if (segment.cas(indexInSegment, null, None())) {
                continue
            }
            return segment.get(indexInSegment)?.let {
                if (it is Some) it.data else null
            }
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() = enqIdx.value <= deqIdx.value
}

private class Segment<T>(val id: Long = 0L) {
    private val next by lazy { Segment<T>(id + 1) }
    private val elements = atomicArrayOfNulls<T>(SEGMENT_SIZE)

    fun get(i: Int): T? = elements[i].value
    fun cas(i: Int, expect: T?, update: T?): Boolean = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: T?) {
        elements[i].value = value
    }
    fun findSegment(segmentId: Long): Segment<T> = if (id == segmentId) this else next.findSegment(segmentId)
}

private sealed class Option<T>
private class Some<T>(val data: T): Option<T>()
private class None<T>: Option<T>()

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

