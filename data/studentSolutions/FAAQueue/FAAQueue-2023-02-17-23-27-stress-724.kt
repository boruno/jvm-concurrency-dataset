package mpp.faaqueue

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
            val curTail = tail
            val index = enqIdx.getAndAdd(1)
            if (index < SEGMENT_SIZE) {
                curTail.value.cas(index.toInt(), null, element)
                return
            }
            if (index >= SEGMENT_SIZE) {
                val curTailNext = curTail.value.next
                val newSegment = Segment()
                newSegment.put(0, element)
                if (curTailNext.compareAndSet(null, newSegment)) {
                    tail.compareAndSet(curTail.value, curTailNext.value!!)
                    return
                } else {
                    tail.compareAndSet(curTail.value, curTailNext.value!!)
                }
            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            val curHead = head
            val index = deqIdx.getAndAdd(-1)
            if (index < SEGMENT_SIZE) {
                val element = curHead.value.get(index.toInt()) ?: continue
                curHead.value.cas(index.toInt(), element, null)
                return null
            }
            val curHeadNext = curHead.value
            if (curHeadNext.next.value == null)
                return null
            head.compareAndSet(curHead.value, curHeadNext.next.value!!)
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            while (true) {
                val curHead = head
                val index = deqIdx.value
                if (index < SEGMENT_SIZE)
                    return false
                val curHeadNext = curHead.value
                if (curHeadNext.next.value == null)
                    return true
                head.compareAndSet(curHead.value, curHeadNext.next.value!!)
            }
        }
}
private class Segment {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

