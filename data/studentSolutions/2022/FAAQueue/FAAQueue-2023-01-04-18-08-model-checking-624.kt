package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment(0)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    private fun findSegment(start: Segment, idx: Long) : Segment {
        if (start.startIndex.toLong() == idx) {
            return start
        }
        if (start.startIndex.toLong() > idx) {
            TODO("what!!")
        }
        while (true) {
            val oldv = start.next.value;
            var newv = oldv;
            if (oldv == null) {
                newv = Segment(start.startIndex + 1)
            }
            if (start.next.compareAndSet(oldv, newv)) {
                break
            }
        }
        return findSegment(start.next.value!!, idx)
    }

    private fun moveTailForward(s : Segment) {
        while (true) {
            val ct = tail;
            val ctv = ct.value
            if (!ctv.equals(s)) {
                if (ct.compareAndSet(ctv, s)) {
                    return
                }
                continue
            }
            return
        }
    }

    private fun moveHeadForward(s : Segment) {
        while (true) {
            val ct = head
            val ctv = ct.value
            if (!ctv.equals(s)) {
                if (ct.compareAndSet(ctv, s)) {
                    return
                }
                continue
            }
            return
        }
    }

    fun enqueue(element: E) {
        while (true) {
            val cur_tail = tail.value
            val i = enqIdx.getAndIncrement()
            val s = findSegment(cur_tail, i / SEGMENT_SIZE)
            moveTailForward(s);
            if (s.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, element)) {
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
            if (isEmpty) {
                return null
            }
            val cur_head = head.value;
            val i = deqIdx.getAndIncrement()
            val s = findSegment(cur_head, i / SEGMENT_SIZE)
            moveHeadForward(s)
            val thing = s.elements[(i % SEGMENT_SIZE).toInt()].getAndSet(EvilThing())//s.elements[(i % SEGMENT_SIZE).toInt()].value
            if (thing == null) {//(s.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, EvilThing())) {
                continue
            }
            return thing as E?
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return enqIdx.value <= deqIdx.value
        }
}

private class Segment(si: Int) {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)
    val startIndex : Int = si

    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

private class EvilThing {};