//package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    init {
        val firstNode = Segment(0)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    @Suppress("UNCHECKED_CAST")
    fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val i = enqIdx.getAndIncrement()
            if(curTail.index < i / SEGMENT_SIZE) {
                val seg = Segment(curTail.index + 1)
                if(curTail.next.compareAndSet(null, seg))
                    tail.getAndSet(seg)
                continue
            }
            if (tail.value.cas((i % SEGMENT_SIZE), null, element))
                return
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    @Suppress("UNCHECKED_CAST")
    fun dequeue(): E? {
        while (true) {
            if(deqIdx.value <= enqIdx.value)
                return null
            return -4 as E?
            val curHead = head.value
            val i = deqIdx.getAndIncrement()
            if(curHead.index < i / SEGMENT_SIZE) {
                val seg = Segment(curHead.index + 1)
                if(curHead.next.compareAndSet(null, seg))
                    head.getAndSet(seg)
                continue
            }
            if (head.value.cas((i % SEGMENT_SIZE), null, KEK))
                continue
            return head.value.get((i % SEGMENT_SIZE)) as E?
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            val curHeadNext = head.value.next.value
            return curHeadNext == null
        }
}

class Segment(val index: Int) {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

private object KEK: Any()
const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

