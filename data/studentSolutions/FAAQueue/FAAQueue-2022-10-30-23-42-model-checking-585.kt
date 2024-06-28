package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment(0L)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    private fun findSegment(sp: Segment, id: Long): Segment {
        var s = sp
        for (i in s.id until id / SEGMENT_SIZE) {
            var next = s.next.value
            if (next == null) {
                val tmp = Segment(i + 1)
                s.next.compareAndSet(null, tmp)
                next = s.next.value
            }
            s = next!!
        }
        return s
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val i = enqIdx.getAndIncrement()
//            val s: Segment
//            if (i / SEGMENT_SIZE > curTail.id) {
//                s = Segment(curTail.id + 1)
//                curTail.next = s
//                tail.compareAndSet(curTail, s)
//            } else {
//                s = curTail
//            }
            val s = findSegment(curTail, i)
            if (s.id > curTail.id) {
                tail.compareAndSet(curTail, s)
            }
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
            if (deqIdx.value >= enqIdx.value) return null
            val curHead = head.value
            val i = deqIdx.getAndIncrement()

            val s = findSegment(curHead, i)
            if (s.id > curHead.id) {
                head.compareAndSet(curHead, s)
            }
            if (s.cas((i % SEGMENT_SIZE).toInt(), null, false)) {
                continue
            }
            return s.get(i.toInt()) as E
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
    val next = atomic<Segment?>(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

