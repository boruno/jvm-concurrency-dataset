//package mpp.faaqueue

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

    enum class Direction{
        enq,
        deq
    }


    private fun findSegment(index: Int, direction: Direction): Segment // index / SegmentSize
    {
        if (direction == Direction.enq) {
            return tail.value
        }
        else {
            var curTail = tail.value
            for (i in 0 until index) {
                curTail = curTail.next!!
            }
            return curTail
        }
    }


    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            if (isEmpty) return
            val enq = enqIdx.getAndIncrement().toInt()
            val s = findSegment(enq / SEGMENT_SIZE, Direction.enq)
            tail.value = s
            var success = true
            for (i in 0 until SEGMENT_SIZE)
            {
                if (s.elements[i].compareAndSet(null, element)) {
                    return
                }
                else {
                    success = false
                }
            }
            if (!success) {
                tail.value = s.next!!
            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (isEmpty) return null
            val deq = deqIdx.getAndIncrement().toInt()
            val s = findSegment(deq / SEGMENT_SIZE, Direction.deq)
            head.value = s
            if (s.elements[deq % SEGMENT_SIZE].compareAndSet(null, "⟂"))
                continue
            return s.elements[deq % SEGMENT_SIZE].value as E?
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
    var next: Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

