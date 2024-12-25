//package mpp.faaqueue

import kotlinx.atomicfu.*
import java.util.concurrent.atomic.AtomicReference

class FAAQueue<E> {
    private val head: AtomicRef<Segment<E>> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment<E>> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment<E>(0)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        do{
            val curTail = tail.value
            val index = enqIdx.getAndIncrement()
            val s = findSegment(curTail, index / SEGMENT_SIZE)
            moveTailForward(s)
            val segmentIndex = (index % SEGMENT_SIZE).toInt()
        } while (!s.cas(segmentIndex, null, Pair(element, null)))
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (!isEmpty) {
            val curHead = head.value
            val index = deqIdx.getAndIncrement()
            val s = findSegment(curHead, index / SEGMENT_SIZE)
            moveHeadForward(s)
            val segmentIndex = (index % SEGMENT_SIZE).toInt()
            if (!s.cas(segmentIndex, null, Pair(null, BROKEN)))
                return s.get(segmentIndex)!!.first
        }
        return null
    }

    private fun findSegment(start: Segment<E>, id: Long): Segment<E>{
        var res = start
        for (i in ((start.id)..id)) {
            res.next.compareAndSet(null, Segment(i))
            res = res.next.get()!!
        }
        return res
    }

    private fun moveTailForward(s: Segment<E>){
        do {
            val curTail = tail.value
            tail.compareAndSet(curTail, curTail.next.get()!!)
        } while(curTail.id < s.id)
    }

    private fun moveHeadForward(s: Segment<E>){
        do {
            val curHead = head.value
            head.compareAndSet(curHead, curHead.next.get()!!)
        } while(curHead.id < s.id)

    }
    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.value >= enqIdx.value
        }
}

private class Segment<E>(val id: Long) {
    var next: AtomicReference<Segment<E>?> = AtomicReference(null)
    val elements = atomicArrayOfNulls<Pair<E?, String?>>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Pair<E?, String?>?, update: Pair<E?, String?>?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: E?) {
        elements[i].value = Pair(value, null)
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

const val BROKEN = "BROKEN"
