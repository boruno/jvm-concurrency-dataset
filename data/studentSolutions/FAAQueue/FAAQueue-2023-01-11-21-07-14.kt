//package mpp.faaqueue

import kotlinx.atomicfu.*
import java.text.BreakIterator.DONE

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
        while (true) {
            val tail = this.tail.value
            val endInd = enqIdx.getAndIncrement()
            if (endInd >= SEGMENT_SIZE) {
                val newSeg = Segment(element)
                if (tail.next == null) {
                    tail.next = newSeg
                    this.tail.compareAndSet(tail, newSeg)
                    return
                }
                this.tail.compareAndSet(tail, tail.next!!)
            } else if (tail.elements[endInd.toInt()].compareAndSet(null, element)) {
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
            val curHead = this.head.value
            val deqIdx = this.deqIdx.getAndIncrement()
            if (deqIdx >= SEGMENT_SIZE) {
                val nextHead = curHead.next ?: return null
                this.head.compareAndSet(curHead, nextHead)
                continue
            }
            val res = curHead.elements[deqIdx.toInt()].getAndSet(DONE) ?: continue
            return res as E?
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            while (true) {
                val head = this.head.value
                val deqIdx = deqIdx.value
                val enqIdx = enqIdx.value
                if (deqIdx >= SEGMENT_SIZE) {
                    val headNext = head.next ?: return true
                    this.head.compareAndSet(head, headNext)
                    continue
                } else {
                    return deqIdx >= enqIdx
                }
            }
        }
}

private class Segment {
    var next: Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    constructor(){}
    constructor(x: Any?) {
        elements[0].getAndSet(x)
    }

    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

