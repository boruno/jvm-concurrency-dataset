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
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while(true) {
            val curTail = tail.value
            val curEnqIdx = enqIdx.getAndIncrement() // <--- FAA(enqIdx, +1)
            val segment = findSegment(curTail, curEnqIdx)

            if(segment.id > curTail.id)
                while(true) {
                    if(tail.compareAndSet(curTail, segment)) break
                }

            val pos = (curEnqIdx % SEGMENT_SIZE).toInt()

            val el = segment.elements[pos]

            if(el.compareAndSet(null, element))
                return
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while(true) {
            if(deqIdx.value >= enqIdx.value) return null
            val curHead = head.value
            val curDeqIdx = deqIdx.getAndIncrement() // <--- FAA(deqIdx, +1)
            val segment = findSegment(curHead, curDeqIdx)
            if(segment.id > curHead.id)
                while(true) {
                    if(head.compareAndSet(curHead, segment)) break
                }

            val pos = (curDeqIdx % SEGMENT_SIZE).toInt()

            val el = segment.elements[pos]

            if(el.compareAndSet(null, KILL()))
                continue
            return el.value as E
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.value >= enqIdx.value
        }

    private fun findSegment(s: Segment, index: Long): Segment {
        val id = index / SEGMENT_SIZE
        var segment = s

        if(segment.id == id)
            return segment

        while(true) {
            if(segment.next != null && segment.next!!.id == id) {
                segment = segment.next!!
                break
            } else if (segment.next == null) {
                segment = segment.createNext()
                break
            }
        }

        return segment
    }
}

private class Segment(val id: Long = 0) {
    var next: Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }

    fun createNext(): Segment {
        next = Segment(id+1)
        return next!!
    }
}

private class KILL

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

