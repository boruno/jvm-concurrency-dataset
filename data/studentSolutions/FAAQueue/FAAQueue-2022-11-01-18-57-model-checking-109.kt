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

    private fun findSegment(_s : Segment, _i : Long) : Segment {
        var s = _s
        while (s.id < _i) {
            synchronized(s) {
                if (s.next == null) {
                    s.next = Segment(s.id + 1)
                }
            }
            s = s.next!!
        }
        return s
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val currentTail = tail.value
            val i = enqIdx.getAndIncrement()
            val s = findSegment(currentTail, i)
            if (tail.value.id < s.id) {
                tail.value = s
            }
            if (s.elements[i.mod(SEGMENT_SIZE)].compareAndSet(null, element)) {
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
            if (deqIdx.value <= enqIdx.value) {
                return null
            }
            val currentHead = head.value
            val i = deqIdx.getAndIncrement()
            val s = findSegment(currentHead, i / SEGMENT_SIZE)
            if (head.value.id < s.id) {
                head.value = s
            }
            if (s.elements[i.mod(SEGMENT_SIZE)].compareAndSet(null, ELEMENT_BREAKER)) {
                continue
            }
            return s.elements[i.mod(SEGMENT_SIZE)].value as E?
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.value <= enqIdx.value
        }
}

private class Segment(val id : Int) {

    var next: Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) =
        if (i < SEGMENT_SIZE) elements[i].compareAndSet(expect, update) else false

    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }

}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS
const val ELEMENT_BREAKER = Long.MAX_VALUE

