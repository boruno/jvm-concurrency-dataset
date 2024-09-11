package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)
    private val BROKEN = Object()

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

            val s = findSegment(curTail, i / SEGMENT_SIZE)

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
            if (deqIdx.value >= enqIdx.value) {
                return null
            }

            val curHead = head.value
            val i = deqIdx.getAndIncrement()
            val idx = i % SEGMENT_SIZE
            if (idx < SEGMENT_SIZE) {
                return if (curHead.cas(idx.toInt(), null, BROKEN)) {
                    continue
                } else if (curHead.cas(idx.toInt(), BROKEN, BROKEN)) {
                    null
                } else {
                    curHead.get(idx.toInt()) as E
                }
            } else {
                val headNext = head.value.next.value
                if (headNext != null) {
                    if (head.value.next.compareAndSet(curHead, headNext)) {
                        continue
                    }
                } else {
                    return null
                }
            }
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return false
        }

    private fun findSegment(start: Segment, id: Long): Segment {
        var s = start
        for (i in s.id .. id) {
            var next = s.next.value
            if (next == null) {
                val newSegment = Segment(i + 1)
                s.next.compareAndSet(null, newSegment)
                next = s.next.value
            }
            s = next!!
        }
        return s
    }
}

class Segment(val id: Long) {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

