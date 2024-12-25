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

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val i = enqIdx.getAndIncrement()
            val s: Segment = if (i % SEGMENT_SIZE != 0L)
                tail.value
            else
            /*curTail.next ?: */ moveTailForward()
            if (s.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, element)) {
                return
            }
        }
    }

    private fun moveTailForward(): Segment {
        val s = Segment()
//        while (true) {
        val curTail = tail.value
        val curTailNext = curTail.next
        return if (curTailNext == null) {
            curTail.next = s
            tail.compareAndSet(curTail, s)
            s
        } else {
            tail.compareAndSet(curTail, curTailNext)
            curTailNext
        }
//        }
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
//            val curHead = head.value
//            val curHeadNext = curHead.next
            val i = deqIdx.getAndIncrement()
            val s: Segment = if (i % SEGMENT_SIZE != 0L)
                head.value
            else
                moveTailForward()
            if (s.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, wtf)) {
                continue
            }
            return s.elements[(i % SEGMENT_SIZE).toInt()].value as E?
        }
    }

    private fun moveHeadForward(): Segment? {
        while (true) {
            val curHead = head.value
            val curTail = tail.value
            val curHeadNext = curHead.next
            if (curHead == curTail) {
                if (curHeadNext == null) {
                    return null
                }
                tail.compareAndSet(curTail, curHeadNext)
            } else {
                if (head.compareAndSet(curHead, curHeadNext!!)) {
                    return curHeadNext
                }
            }
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return enqIdx.value == deqIdx.value
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

private class WTF

private val wtf = WTF()

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

