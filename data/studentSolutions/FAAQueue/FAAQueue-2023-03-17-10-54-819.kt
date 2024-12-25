//package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val bom = Bom()
    private class Bom

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
            val tailL = tail.value
            val n = tailL.next.value
            val e = tailL.enqIdx.getAndIncrement()

            if (e >= SEGMENT_SIZE) {
                val nSeg = Segment()
                nSeg.put(0, element)
                if (tailL.next.compareAndSet(null, nSeg))
                    break
                else {
                    tail.compareAndSet(tailL, n!!)
                    continue
                }
            } else if (tailL.cas(e.toInt(), null, element))
                break
        }

    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            val headH = head.value
            val d = headH.deqIdx.getAndIncrement()

            if (d >= SEGMENT_SIZE) {
                val nHead = headH.next.value ?: return null
                head.compareAndSet(headH, nHead)
            } else {
                if (headH.cas(d.toInt(), null, bom))
                    continue
                else
                    return headH.get(d.toInt()) as E?
            }
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            while (true) {
                val curHead = head.value
                val i = curHead.deqIdx.value
                if (i >= curHead.enqIdx.value || i >= SEGMENT_SIZE) {
                    val nextHead = curHead.next.value ?: return true // queue is empty
                    head.compareAndSet(curHead, nextHead) // roll again
                } else {
                    return false
                }
            }
        }
}

private class Segment {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)
    val enqIdx = atomic(0L)
    val deqIdx = atomic(0L)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS



