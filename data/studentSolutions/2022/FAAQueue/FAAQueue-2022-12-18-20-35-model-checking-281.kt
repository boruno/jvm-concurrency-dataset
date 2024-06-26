package mpp.faaqueue

import kotlinx.atomicfu.*
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

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
            val cur_tail = tail.value
            val i = enqIdx.getAndIncrement()
            var s = cur_tail

            if (i > 0 && (i % SEGMENT_SIZE).toInt() == 0) {
                s.casNext(null, Segment())
                s = s.next.value!!
            }

            tail.compareAndSet(cur_tail, s)

            if (s.cas((i % SEGMENT_SIZE).toInt(), null, Optional.ofNullable(element))) {
                return
            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    @Suppress("UNCHECKED_CAST")
    fun dequeue(): E? {
        var i = deqIdx.getAndIncrement()
        while (true) {
            if (deqIdx.value >= enqIdx.value) {
                return null
            }
            val cur_head = head.value
            var s = cur_head

            if (i > 0 && (i % SEGMENT_SIZE).toInt() == 0) {
                s.casNext(null, Segment())
                s = s.next.value!!
            }

            head.compareAndSet(cur_head, s)

            if (s.cas((i % SEGMENT_SIZE).toInt(), null, Optional.empty<E>())) {
                i = deqIdx.getAndIncrement()
                continue
            }

            val res = s.get((i % SEGMENT_SIZE).toInt())

            if (res != null && ((res as Optional<*>).isEmpty ||
                !s.cas((i % SEGMENT_SIZE).toInt(), res, Optional.empty<E>()))) {
                continue
            }

            return (res as Optional<*>?)?.orElse(null) as E?
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

private class Segment {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }

    fun casNext(expect: Segment?, update: Segment?) {
        next.compareAndSet(expect, update)
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

