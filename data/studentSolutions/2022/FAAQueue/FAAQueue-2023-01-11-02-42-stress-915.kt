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
            var currS = tail.value
            val i = enqIdx.getAndIncrement() // FAA(&enqIdx, +1) TODO rewrite to another

            // findSegment(start = currTail, id = i / SEGMENT_SIZE)
            val id = i / SEGMENT_SIZE
            val segIndex = i % SEGMENT_SIZE

            while (currS.currentNumberBlock < id) { // TODO !=
                val currNext = currS.next.value
                if (currNext == null) {
                    val newSegment = Segment().apply { currentNumberBlock = (id + 1).toInt() }
                    currS.next.compareAndSet(null, newSegment)
                } else currS = currS.next.value!!
            }

            while (true) {
                val cur_tail = tail.value
                if (cur_tail.currentNumberBlock >= currS.currentNumberBlock) break
                if (tail.compareAndSet(cur_tail, currS))
                    break
            }
            if (currS.cas(segIndex.toInt(), null, element)) return
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            var currS = head.value
            val deq = deqIdx.value
            val enq = enqIdx.value
            if (deq >= enq) return null

            val i = deqIdx.getAndIncrement()
            val id = i / SEGMENT_SIZE
            val segIndex = i % SEGMENT_SIZE


            while (currS.currentNumberBlock < id) { // TODO !=
                val currNext = currS.next.value
                if (currNext == null) {
                    val newSegment = Segment().apply { currentNumberBlock = (id + 1).toInt() }
                    currS.next.compareAndSet(null, newSegment)
                } else currS = currS.next.value!!
            }


            while (true) {
                val cur_tail = head.value
                if (cur_tail.currentNumberBlock >= currS.currentNumberBlock) break
                if (head.compareAndSet(cur_tail, currS))
                    break
            }

            if (currS.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, Any()))
                continue
            return currS.elements[(i % SEGMENT_SIZE).toInt()].value as E
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
    var currentNumberBlock = 0
    val next = atomic<Segment?>(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

