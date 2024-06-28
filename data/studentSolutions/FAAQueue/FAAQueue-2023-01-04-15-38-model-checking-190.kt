package mpp.faaqueue

import kotlinx.atomicfu.*
import java.lang.Exception

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

    private fun findSegmentById(tail: Segment, id: Long): Segment {
        var tmp = tail
        while (tmp.id < id) {
            if (tmp.next.value == null) {
                val newSeg = Segment(id)
                tmp.next.compareAndSet(null, newSeg)
            }
            tmp = tmp.next.value!!
        }

        return tmp
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
//        println("enqueue enter")
        while (true) {
            val curTail = tail.value
            val i = enqIdx.addAndGet(1) - 1
            val seg = findSegmentById(curTail, i / SEGMENT_SIZE)
            if (seg.id != curTail.id) {
                tail.compareAndSet(curTail, seg)
            }
            if (seg.cas((i % SEGMENT_SIZE).toInt(), null, element)) {
                break
            }
        }
//        println("enqueue end")
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
//        println("deq enter")
        while (true) {
            val dec = deqIdx.value
            val enq = enqIdx.value

            if (dec > enq) {
                return null
            }
            val curHead = head.value
            val i = deqIdx.addAndGet(1) - 1
            val seg = findSegmentById(curHead, i / SEGMENT_SIZE)

            if (seg.id != curHead.id) {
                head.compareAndSet(curHead, seg)
            }
            if (seg.cas((i % SEGMENT_SIZE).toInt(), null, myEmptyType())) {
                continue
            }
            if (seg.id != i / SEGMENT_SIZE) {
                throw Exception("wtf")
            }
            if (seg.get((i % SEGMENT_SIZE).toInt()) is myEmptyType) {
                throw Exception("aboba")
            }
//            println("deq exit")
            return seg.get((i % SEGMENT_SIZE).toInt()) as E?
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return enqIdx.value == deqIdx.value
        }
}

private class myEmptyType {}

private class Segment(val id: Long) {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    public fun get(i: Int) = elements[i].value
    public fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    public fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

