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
            val cur_tail = tail
            val i = enqIdx.incrementAndGet()
            var fl = 1
            while ( i > cur_tail.value.id * SEGMENT_SIZE ) {
                fl = 0
                if ( cur_tail.value.next.compareAndSet( null, Segment() ) ) {
                    cur_tail.value.next.value!!.id = cur_tail.value.id + 1
                }
                cur_tail.getAndSet( cur_tail.value.next.value!! )
            }
            if ( fl.equals( 0 ) ) {
                tail.compareAndSet(cur_tail.value, cur_tail.value.next.value!!)
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
            var cur_head = head
            val i = deqIdx.incrementAndGet().toInt()
            if (i > SEGMENT_SIZE * ( cur_head.value.id + 1 ) ) {
                if ( cur_head.value.next.value != null ) {
                    head.compareAndSet( cur_head.value, cur_head.value.next.value!! )
                    cur_head.getAndSet( cur_head.value.next.value!! )
                } else {
                    return null
                }
            }
            if ( cur_head.value.elements[ ( i + 1 ) % SEGMENT_SIZE ].compareAndSet( null, -1333 ) ) {
                continue
            } else {
                if (!cur_head.value.elements[(i + 1) % SEGMENT_SIZE].value!!.equals(-1333))
                    return cur_head.value.elements[ ( i + 1 ) % SEGMENT_SIZE ].value as E?
                else
                    return null
            }
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

