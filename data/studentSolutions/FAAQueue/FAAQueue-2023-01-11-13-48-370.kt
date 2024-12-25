//package mpp.faaqueue

import kotlinx.atomicfu.*
import java.util.concurrent.locks.ReentrantLock

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)
    //var lock: ReentrantLock
    init {
        val firstNode = Segment(0)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     */


    private fun moveTail(tail1: Segment, id: Int) : Segment {
        var curTail = tail1
        while (true) {
            if (id / SEGMENT_SIZE > curTail.id) {
                val nextSegment = Segment(curTail.id + 1)
                curTail.next.compareAndSet(null, nextSegment)
                tail.compareAndSet(curTail, curTail.next.value!!)
                curTail = tail.value
            } else {
                return curTail
            }
        }
    }

    private fun moveHead(head1: Segment, id: Int) : Segment {
        var curHead = head1
        while (true) {
            //println("deq - ${deqIdx.value} enq - ${enqIdx.value}")
            if (id / SEGMENT_SIZE > curHead.id) {
                val nextSegment = Segment(curHead.id + 1)
                curHead.next.compareAndSet(null, nextSegment)
                head.compareAndSet(curHead, curHead.next.value!!)
                curHead = head.value
            } else {
                return curHead
            }
        }

        /*val curHead = head.value
        val nextSegment: Segment
        if (id / SEGMENT_SIZE > curHead.id) {
            nextSegment = Segment((id / SEGMENT_SIZE).toLong())
            curHead.next.compareAndSet(null, nextSegment)
            head.compareAndSet(curHead, curHead.next.value!!)
        }
        return if (curHead != head.value) {
            nextSegment
        } else {
            curHead
        }*/
    }

    fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val i = enqIdx.getAndIncrement()
            val s = moveTail(curTail, i.toInt())
            //println("deq - ${deqIdx.value} enq - ${enqIdx.value}")
            /*if (s.id > curTail.id) {
                //tail.compareAndSet(curTail, s)
                tail.value = s
            }*/
            if (s.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, element)) {
                //println ("element - $element on ${(i % SEGMENT_SIZE).toInt()} place in ${s.id} segment")
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
            if (isEmpty) {
                return null
            }
            val curHead = head.value
            val i = deqIdx.getAndIncrement()
            val s = moveHead(curHead, i.toInt())
            /*if (s.id > curHead.id) {
                head.value = s
            }*/
            if (s.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, -69420)) {
                continue
            }
            //println ("deleted value - ${s.elements[(i % SEGMENT_SIZE).toInt()].value as E?}")
            return s.elements[(i % SEGMENT_SIZE).toInt()].value as E?
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            if (deqIdx.value >= enqIdx.value) {
                return true
            }
            return false
        }
}

private class Segment(val id: Long) {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

