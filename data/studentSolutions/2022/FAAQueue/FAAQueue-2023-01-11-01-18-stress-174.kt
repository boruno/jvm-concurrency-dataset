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
            val currTail = tail.value
            val i = enqIdx.getAndIncrement() // FAA(&enqIdx, +1)

            // findSegment(start = currTail, id = i / SEGMENT_SIZE)
            val id = i / SEGMENT_SIZE
            if (id > currTail.currentNumberBlock) {
                val newSegment = Segment()
                newSegment.currentNumberBlock = id.toInt()
                if (currTail.next.compareAndSet(null, newSegment))
                    tail.compareAndSet(currTail, newSegment)
            } else {
                if (currTail.cas((i % SEGMENT_SIZE).toInt(), null, element)) return
            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            val currS = head.value
            val i = deqIdx.getAndIncrement() // FAA(&enqIdx, +1)

            // findSegment(start = currTail, id = i / SEGMENT_SIZE)
            val id = i / SEGMENT_SIZE
            if (id > currS.currentNumberBlock) {
                if (currS.next.value == null) return null
                head.compareAndSet(currS, currS.next.value!!)
            } else {
                val currValueByIdx = currS.elements[(i % SEGMENT_SIZE).toInt()].getAndSet(Any())
                if (currValueByIdx != null) return currValueByIdx as E
            }
        }
    }


    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            TODO("implement me")
        }
}

private class Segment {
    var currentNumberBlock = -1
    val next = atomic<Segment?>(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

