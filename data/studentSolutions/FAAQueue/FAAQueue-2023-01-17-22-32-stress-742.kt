package mpp.faaqueue

import kotlinx.atomicfu.*

val DONE = Any()
class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue

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
            val t = tail.value
            val idx = t.enqIdx.getAndIncrement()
            if (idx >= SEGMENT_SIZE) {
                val s = Segment()
                s.put(0, element)
                s.enqIdx.getAndIncrement()
                if (t.next.compareAndSet(null, s)) {
                    tail.compareAndSet(t, s)
                    return
                }
                tail.compareAndSet(t, t.next.value!!)
                continue
            }
            if (t.cas(idx.toInt(), null, element)) {
                return
            }
            assert(t.get(idx.toInt()) == DONE)
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            val h = head.value
            val idx = h.deqIdx.getAndIncrement()
            if (idx >= SEGMENT_SIZE) {
                val next = h.next.value ?: return null
                head.compareAndSet(h, next)
                continue
            }
            val x = h.get(idx.toInt())
            if (x == null) {
                if (h.cas(idx.toInt(), null, DONE)) {
                    return null
                }
                return h.get(idx.toInt()) as E
            }
            return x as E
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            val h = head.value
            val idx1 = h.enqIdx.value
            val idx2 = h.deqIdx.value

            return idx2 >= idx1
        }
}

private class Segment {
    val enqIdx = atomic(0L)
    val deqIdx = atomic(0L)
    val next = atomic<Segment?>(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 1024 // DO NOT CHANGE, IMPORTANT FOR TESTS
