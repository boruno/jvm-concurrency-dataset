//package mpp.faaqueue

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

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while(true){
            val curr_tail = tail.value
            val index = enqIdx.getAndAdd(1L)
            val segment = findSegment(curr_tail, (index/ SEGMENT_SIZE).toInt())
            moveForward(false, segment)
            if(segment.elements[(index % SEGMENT_SIZE).toInt()].compareAndSet(null, element))
                return
        }

    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while(true){
            if(deqIdx.value >= enqIdx.value)
                return null
            val curr_head = head.value
            val index = deqIdx.getAndAdd(1L)
            val segment = findSegment(curr_head, (index/ SEGMENT_SIZE).toInt())
            moveForward(true, segment)
            val res = segment.elements[(index % SEGMENT_SIZE).toInt()].value
            if(segment.elements[(index % SEGMENT_SIZE).toInt()].compareAndSet(null, "broken"))
                continue
            return res as? E
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.value >= enqIdx.value
        }

    fun findSegment(seg: Segment, id: Int): Segment{
        var start = seg
        while(true) {
            if(start.id == id)
                return start
            if(start.next == null)
            {
                val new_seg = Segment(start.id+1)
                start.next = new_seg
                return new_seg
            }
            start = start.next!!
        }
    }

    fun moveForward(isHead: Boolean, seg: Segment){
        if(isHead){
            val curr_head = head.value
            if(curr_head == seg)
                return
            if(head.compareAndSet(curr_head, seg))
                return
        }
        else{
            while(true){
                val curr_tail = tail.value
                if(curr_tail.id >= seg.id)
                    return
                else
                    if(tail.compareAndSet(curr_tail, seg))
                        return
            }
        }
    }
}

class Segment(_id: Int) {
    var next: Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)
    var id = _id;

    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

