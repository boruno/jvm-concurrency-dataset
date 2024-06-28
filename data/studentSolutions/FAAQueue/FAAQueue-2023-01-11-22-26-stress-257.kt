package mpp.faaqueue

import kotlinx.atomicfu.*
import java.beans.ExceptionListener

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment()
        firstNode.id = 1
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while ( true ) {
            var copy_tail = tail.value
            var cur_tail  = tail.value
            val i = enqIdx.incrementAndGet()
            var seg = Segment()
            if ( cur_tail.next.compareAndSet( null, seg ) ) {
                cur_tail.next.value!!.id = cur_tail.id + 1
            }
            while ( i > cur_tail.id * SEGMENT_SIZE ) {
                var seg1 = Segment()
                if ( cur_tail.next.compareAndSet( null, seg1 ) ) {
                    cur_tail.next.value!!.id = cur_tail.id + 1
                }
                cur_tail = cur_tail.next.value!!
            }
            if ( !tail.compareAndSet( copy_tail, cur_tail ) ) {
                continue
            }
            if ( tail.value.elements[ ( i % SEGMENT_SIZE ).toInt() ].compareAndSet( null, element ) ) {
                    return
                }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while ( true ) {
            var head_copy = head.value
            var cur_head = head_copy
            val i = deqIdx.incrementAndGet()
            if ( deqIdx.value > enqIdx.value ) {
                return null
            }
            while (i > SEGMENT_SIZE * cur_head.id ) {
                var seg1 = Segment()
                if ( cur_head.next.compareAndSet( null, seg1 ) ) {
                    cur_head.next.value!!.id = cur_head.id + 1
                }
                cur_head = cur_head.next.value!!
            }
            if ( cur_head.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, -1333)) {
                continue
            }
            return cur_head.elements[(i % SEGMENT_SIZE).toInt()].value as E?
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
    val next : AtomicRef<Segment?> = atomic( null )
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)
    var id : Long = 0
    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

