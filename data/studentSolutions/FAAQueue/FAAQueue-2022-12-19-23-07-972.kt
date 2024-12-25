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
        while (true) {
            val curTail = tail.value
            val idx = enqIdx.getAndAdd(1)

            val curSegment = findSegment(curTail, idx / SEGMENT_SIZE)
            println("[${Thread.currentThread().id}] enc $element to seg ${curSegment.id}")
            if (curSegment.id > curTail.id) { //??
                tail.compareAndSet(curTail, curSegment)
            }

            if (curSegment.cas((idx % SEGMENT_SIZE).toInt(), null, element)){
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
            if (isEmpty) return null
            val curHead = head.value
            val idx = deqIdx.getAndAdd(1)

            val curSegment = findSegment(curHead, idx/ SEGMENT_SIZE)
            if (curSegment.id > curHead.id) { //??
                head.compareAndSet(curHead, curSegment)
            }

            if (curSegment.cas((idx % SEGMENT_SIZE).toInt(), null, false)) continue
            try {
                if (curSegment.get((idx % SEGMENT_SIZE).toInt())!!.equals(false)) continue
            } catch (e: Exception) {
                continue
            }
            val e = curSegment.get((idx % SEGMENT_SIZE).toInt()) as? E
            println("[${Thread.currentThread().id}] deq $e from seg ${curSegment.id}")
            return e
        }
    }

    private fun findSegment(s:Segment, id: Long) : Segment {
        if (s.id == id) return s
        var next: Segment? = s
        while (next != null) {
            if (next.id == id) return next
            next = next.next.value
        }
        return Segment(id)
    }
//        if (idx != 0L && idx % SEGMENT_SIZE == 0L) {
//            val curTail = tail.value
//            val newTail = Segment()
//            if (curTail.next.compareAndSet(null, newTail)) {
//                tail.compareAndSet(curTail, newTail)
//            } else {
//                tail.compareAndSet(curTail, curTail.next.value!!)
//            }
//        }
//    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.value >= enqIdx.value
        }
}

private class Segment(val id: Long) {
    val next = atomic<Segment?>(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

