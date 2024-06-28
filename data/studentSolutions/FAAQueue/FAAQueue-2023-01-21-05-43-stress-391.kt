package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment<E>> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment<E>> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment<E>()
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    private fun findSegment(start: Segment<E>, id: Long): Segment<E> {
        assert (start.index <= id)
        if (start.index == id) return start
        var next = Segment<E>()
        next.index = start.index + 1
        start.next.compareAndSet(null, next)
        return findSegment(start.next.value!!, id)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val cut_tail = tail.value
            val i: Long = enqIdx.getAndIncrement() 
            val s = findSegment(cut_tail, i / SEGMENT_SIZE)
            tail.compareAndSet(cut_tail, s)
            if (s.cas(i.mod(SEGMENT_SIZE), null, Done<E>(false, element))) {
                return
            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        if (deqIdx.value >= enqIdx.value) return null
        val cur_head = head.value
        val i = deqIdx.getAndIncrement()
        val s = findSegment(cur_head, i / SEGMENT_SIZE)
        head.compareAndSet(cur_head, s)
        return head.value.get(i.mod(SEGMENT_SIZE))?.element
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            val deq = deqIdx.value
            val enq = enqIdx.value
            return deq == enq
        }
}

private data class Done<E> (val done: Boolean, val element: E) 

private class Segment<E> {
    val next: AtomicRef<Segment<E>?> = atomic(null)
    val elements = atomicArrayOfNulls<Done<E>?>(SEGMENT_SIZE)
    var index: Long = 0

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Done<E>?, update: Done<E>?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Done<E>?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

