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
        while (true) {
            val curTail = tail.value
            val index = enqIdx.getAndIncrement().toInt()
            val segment = findSegment(curTail, index / SEGMENT_SIZE)
            moveTailForward(segment)
            if(segment.cas(index % SEGMENT_SIZE, null, element))
                return
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            val curHead = head.value
            if (deqIdx.value <= enqIdx.value)
                return null
            val index = deqIdx.getAndIncrement().toInt()
            val segment = findSegment(curHead, index / SEGMENT_SIZE)
            moveHeadForward(segment)
            if(segment.cas(index % SEGMENT_SIZE, null, "kek"))
                continue
            return segment.get(index) as E
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            if (deqIdx.value <= enqIdx.value)
                return true
            return false
        }

    private fun findSegment(start: Segment, id: Int): Segment {
        var newStart = start
        while (newStart.getId() <= id) {
            if (newStart.next.value == null) {
                val newSegment = Segment()
                newSegment.setId(id)
                return newSegment
            }
            newStart = start.next.value!!
        }
        return newStart
    }

    private fun moveTailForward(newCur: Segment){
        while (newCur.getId() > tail.value.getId()){
            val curTail = tail.value
            if(tail.compareAndSet(curTail, newCur))
                return
        }
    }

    private fun moveHeadForward(newCur: Segment){
        while (newCur.getId() > head.value.getId()){
            val curTail = head.value
            if(head.compareAndSet(curTail, newCur))
                return
        }
    }
}
class Segment {
    val next: AtomicRef<Segment?> = atomic(null)
    private val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)
    private var id = 0
    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
    fun setId(i :Int){
        id = i
    }
    fun getId(): Int{
        return id
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

