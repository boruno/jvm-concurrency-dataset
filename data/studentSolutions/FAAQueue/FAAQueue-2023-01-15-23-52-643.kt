//package mpp.faaqueue

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

    private fun findSegment(start: Segment, id: Long): Segment {
        var curr_id = start.id
        var segment: Segment = start
        while (curr_id < id) {
            if (segment.next == null) {
                curr_id++
                val old = segment
                segment = Segment(curr_id)
                old.next = segment
            } else {
                segment = segment.next!!
                curr_id = segment.id
            }
        }
        return segment
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val cur_tail = tail.value
            val i = enqIdx.getAndIncrement()
            val s = findSegment(start = cur_tail, id = i / SEGMENT_SIZE)
            if (s != cur_tail) {
                if ( ! tail.compareAndSet(cur_tail, s)) {
                    continue
                }
            }

            if (s.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, element)) {
                return
            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    @Suppress("UNCHECKED_CAST")
    fun dequeue(): E? {
        while (true) {
            if (deqIdx.value >= enqIdx.value) {
                return null
            }
            val cur_head = head.value
            val i = deqIdx.getAndIncrement()
            val s = findSegment(start = cur_head, id = i / SEGMENT_SIZE)
            if (s != cur_head) {
                if ( ! head.compareAndSet(cur_head, s)) {
                    continue
                }
            }
            return (s.elements[(i % SEGMENT_SIZE).toInt()].getAndSet(null) ?: continue) as E?
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            while (true) {
                val curr_deq = deqIdx.value
                val curr_enq = enqIdx.value
                if (enqIdx.compareAndSet(curr_deq, curr_deq)) {
                    if (deqIdx.compareAndSet(curr_enq, curr_enq))
                        return true
                }
                if (deqIdx.compareAndSet(curr_deq, curr_deq)) {
                    return false
                }
            }
//            while (true) {
//                head.value.elements.get()
//            }
        }
}

private class Segment(val id: Long) {
    var next: Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)
//    val id: Long = -1

    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

