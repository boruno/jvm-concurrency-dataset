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

    private fun findSegmentForTail(curTail: Segment, ind: Long): Segment {
        return if(ind < SEGMENT_SIZE) {
            curTail
        } else {
            val newSegment = Segment()
            curTail.next = newSegment
            newSegment
        }
    }

    private fun findSegmentForHead(curHead: Segment, ind: Long): Segment {
        return if(ind < SEGMENT_SIZE) {
            curHead
        } else {
            curHead.next!!
        }
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while(true) {
            val curTail = tail.value
            val ind = enqIdx.getAndIncrement()
            val segment = findSegmentForTail(curTail, ind)
            if(ind >= SEGMENT_SIZE) {
                if(!tail.compareAndSet(curTail, segment)) {
                    continue
                }
            }
            if(segment.cas((ind % SEGMENT_SIZE).toInt(), null, element)) {
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
           if(isEmpty) {
               return null
           }
           val curHead = head.value
           val ind = deqIdx.getAndIncrement()
           val segment = findSegmentForHead(curHead, ind)
           if(ind >= SEGMENT_SIZE) {
               if(!head.compareAndSet(curHead, segment)) {
                   continue
               }
           }
           if(segment.cas((ind % SEGMENT_SIZE).toInt(), null, null)) {
               continue
           }
           return segment.get(ind.toInt()) as E?
       }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            if(head.value == tail.value) {
                return enqIdx.value == deqIdx.value
            }
            return false
        }
}

private class Segment {
    var next: Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

