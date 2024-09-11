package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment()
        firstNode.id = 0
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
            if ( tail.value.elements[ i % SEGMENT_SIZE ].compareAndSet( null, element ) ) {
                return
            } else {
                cur_tail.value.next.compareAndSet( null, Segment() )
                cur_tail.value.next.value!!.id = cur_tail.value.id + 1
                tail.compareAndSet( cur_tail.value, cur_tail.value.next.value!! )
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
            if ( deqIdx.value > enqIdx.value ) {
                return null
            }
            if ( deqIdx.value > SEGMENT_SIZE * ( cur_head.value.id + 1 ) ) {
                if ( cur_head.value.next.value != null ) {
                    head.compareAndSet( cur_head.value, cur_head.value.next.value!! )
                } else {
                    return null
                }
            }
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
    val next : AtomicRef<Segment?> = atomic( null )
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)
    var id : Int = 0
    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

