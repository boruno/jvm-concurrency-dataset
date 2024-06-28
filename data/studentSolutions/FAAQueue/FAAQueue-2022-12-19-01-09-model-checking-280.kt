package mpp.faaqueue

import kotlinx.atomicfu.*
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)
    private val segIdx = atomic(0L)

    init {
        val firstNode = Segment(segIdx.getAndIncrement())
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

            for (j in s.id until i / SEGMENT_SIZE) {
                if (s.next.value == null) {
                    s.casNext(null, Segment(segIdx.getAndIncrement()))
                }
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
        while (true) {
            if (deqIdx.value >= enqIdx.value) {
                return null
            }
            val cur_head = head.value
            val i = deqIdx.getAndIncrement()
            var s = cur_head

            for (j in s.id until i / SEGMENT_SIZE) {
                if (s.next.value == null) {
                    s.casNext(null, Segment(segIdx.getAndIncrement()))
                }
                s = s.next.value!!
            }

            head.compareAndSet(cur_head, s)

            if (s.cas((i % SEGMENT_SIZE).toInt(), null, Optional.empty<E>())) {
                continue
            }

            val res = s.get((i % SEGMENT_SIZE).toInt())
//            println("$i ${s.id} $res")

            if (res != null && ((res as Optional<*>).isEmpty ||
                !s.cas((i % SEGMENT_SIZE).toInt(), res, Optional.empty<E>()))) {
                continue
            }

//            println("$i ${s.id} $res")

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

private class Segment(val id: Long) {
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

