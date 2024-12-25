//package mpp.faaqueue

import kotlinx.atomicfu.*

val BREAK = Any()
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
        while(true) {
            var curTail = tail.value
            val i = enqIdx.getAndIncrement()
            if (i >= SEGMENT_SIZE && i % SEGMENT_SIZE == 0L) {
                val newTail = Segment()
                if (tail.compareAndSet(curTail, newTail)) {
                    curTail.next = newTail
                    curTail = tail.value
                }
                else continue
            }
//            while (i < s.id) {
//                val newTail = Segment()
//                if (tail.compareAndSet(curTail, newTail)) {
//                    curTail.next = newTail
//                    curTail = tail.value
//                }
//                else continue
//            }
            if (curTail.cas((i % SEGMENT_SIZE).toInt(), null, element)) {
                return
            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while(true) {
            if (enqIdx.value <= deqIdx.value) {
                return null
            }
            var curHead = head.value
            val i = deqIdx.getAndIncrement()
            if (i >= SEGMENT_SIZE && i % SEGMENT_SIZE == 0L) {
                val newHead = curHead.next ?: return null
                if (head.compareAndSet(curHead, newHead)) {
                    curHead = head.value
                }
                else continue
            }
            if (curHead.cas((i % SEGMENT_SIZE).toInt(), null, BREAK)) {
                continue
            }
            return curHead.get((i % SEGMENT_SIZE).toInt()) as E?
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            if (deqIdx.value <= enqIdx.value) {
                return true
            }
            return false
        }
}

private class Segment {
    var next: Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    public fun get(i: Int) = elements[i].value
    public fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    public fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

