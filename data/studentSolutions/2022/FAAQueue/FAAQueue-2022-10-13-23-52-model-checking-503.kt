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

    private fun findSegmentForTail(curTail: Segment, segmentId: Long): Segment {
        return if(curTail.id == segmentId) {
            curTail
        } else {
            val newSegment = Segment(segmentId)
            curTail.next = newSegment
            newSegment
        }
    }

    private fun findSegmentForHead(curHead: Segment, segmentId: Long): Segment? {
        return if(curHead.id == segmentId) {
            curHead
        } else {
            curHead.next
        }
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while(true) {
            val curTail = tail.value
            val ind = enqIdx.getAndIncrement()
            val segment = findSegmentForTail(curTail, ind / SEGMENT_SIZE)

            if(segment.id > tail.value.id) { // move tail forward
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

           val indInSegment = (ind % SEGMENT_SIZE).toInt()
//           if(ind >= SEGMENT_SIZE) {
//               if(!head.compareAndSet(curHead, segment)) {
//                   continue
//               }
//           }
//           if(indInSegment == SEGMENT_SIZE - 1) { // move head forward
//               if(!head.compareAndSet(curHead, segment.next!!)) {
//                   continue
//               }
//           }
           if(segment == null)
               continue
           if(segment.id > head.value.id) { // move head forward
               if(!head.compareAndSet(curHead, segment)) {
                   continue
               }
           }

           if(segment.cas(indInSegment, null, null)) {
               continue
           }
           return segment.get(indInSegment) as E?
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

private class Segment(val id: Long) {
    var next: Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

