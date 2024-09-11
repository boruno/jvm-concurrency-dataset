package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment()
        firstNode.next = Segment()
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while ( true ) {
            var cur_tail = tail
            val i = enqIdx.incrementAndGet().toInt()
            if ( tail.value.elements[ i % SEGMENT_SIZE ].compareAndSet( null, element ) )
                return
            else {
                if ( tail.compareAndSet( cur_tail.value, cur_tail.value.next!! ) ) {
                    tail.value.next = Segment()
                }
            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        return null
        while ( true ) {
            var cur_head = head
            if ( deqIdx.value >= enqIdx.value ) {
                return null
            }
            val i = deqIdx.incrementAndGet().toInt()
            if ( cur_head.value.elements[ i % SEGMENT_SIZE ].compareAndSet( null, -1333 ) ) {
                continue
            }
            return cur_head.value.elements[ i % SEGMENT_SIZE ].value as E?
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

private class Segment {
    var next : Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

