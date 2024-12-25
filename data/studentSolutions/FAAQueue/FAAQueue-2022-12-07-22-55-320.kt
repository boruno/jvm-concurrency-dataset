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
        while (true){
            val cur_tail = tail
            val i = enqIdx.addAndGet(1)
            if ((i % SEGMENT_SIZE).toInt() == 0){
                tail.value = Segment()
                cur_tail.value.next = tail.value;
            }
            if (tail.value.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, element)){
                return
            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true){
            if (deqIdx.value >= enqIdx.value){
                return null
            }
            val cur_head = head
            val i = deqIdx.addAndGet(1)
            if ((i % SEGMENT_SIZE).toInt() == 0){
                head.value = Segment()
                cur_head.value.next = head.value;
            }
            if (head.value.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, -1)){
                continue
            }
            return head.value.elements[(i % SEGMENT_SIZE).toInt()].value as E?
        }

    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            if (deqIdx.value >= enqIdx.value){
                return true
            }
            return false
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

