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
     * Adds the specified element [element] to the queue.
     */
    fun enqueue(element: E) {
        var cur_tail = tail.value
        val i = enqIdx.getAndIncrement()
        var s: Segment
        s = tail.value
        while (s.next != null) {
            s = s.next!!
            tail.compareAndSet(cur_tail, s)
            cur_tail = tail.value
        }

        cur_tail.elements[i.toInt()].compareAndSet(null, element)
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        var cur_head = head.value

        var s = head.value
        var i = deqIdx.getAndIncrement()
        while (i > 0) {
            s = s.next ?: return null
            head.compareAndSet(cur_head, s)
            cur_head = head.value
            i--
        }
        return cur_head.elements[(i / SEGMENT_SIZE).toInt()].value as E?
    }


    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.value >= enqIdx.value || deqIdx.value >= SEGMENT_SIZE
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

