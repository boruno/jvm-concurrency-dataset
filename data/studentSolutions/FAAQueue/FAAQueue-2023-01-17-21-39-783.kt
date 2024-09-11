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
            var enqVal = enqIdx.value
            while (deqIdx.value > enqVal) {
                if (enqIdx.compareAndSet(enqVal, deqIdx.value)) {
                    return
                }
                enqVal = enqIdx.value
            }

            val curTail = tail.value
            val i = enqIdx.getAndIncrement()

            // find segment (!!! and creates new segment if needed)
            var s = curTail
            val enqueueId = i / SEGMENT_SIZE
            while (enqueueId != s.id) {
                if (s.next.value != null) {
                    s = s.next.value!!
                } else {
                    val newSegment = Segment()
                    newSegment.id = enqueueId
                    if (s.next.compareAndSet(null, newSegment)) {
                        s = newSegment
                        break
                    }
                }
            }

            //moveTailForward
            if (s != curTail) {
                while (true) {
                    val moveTail = tail.value
                    if (moveTail.next.value != null) {
                        tail.compareAndSet(moveTail, moveTail.next.value!!)
                    } else {
                        break
                    }
                }
            }

            // CAS
            if (s.cas((i % SEGMENT_SIZE).toInt(), null, element)) {
                // println("enqueue $element in $i")
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
            if (isEmpty) return null
            val curHead = head.value
            val i = deqIdx.getAndIncrement()
            // println("try dequeue in $i")

            // find segment
            var s = curHead
            val dequeueId = i / SEGMENT_SIZE
            while (dequeueId != s.id) {
                if (s.next.value != null) {
                    s = s.next.value!!
                } else {
                    val newSegment = Segment()
                    newSegment.id = dequeueId
                    if (s.next.compareAndSet(null, newSegment)) {
                        s = newSegment
                        break
                    }
                }
            }

            //moveHeadForward
            if (s != curHead) {
                while (true) {
                    val moveHead = head.value
                    if (moveHead.next.value != null) {
                        head.compareAndSet(moveHead, moveHead.next.value!!)
                    } else {
                        break
                    }
                }
            }

            if (s.cas((i % SEGMENT_SIZE).toInt(), null, "")) {
                continue
            }
            return s.get((i % SEGMENT_SIZE).toInt()) as E
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
    val next :AtomicRef<Segment?> = atomic(null)
    var id: Long = 0L
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

