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
            val newId = enqIdx.getAndAdd(1)
            val s = findSegment(newId / SEGMENT_SIZE, curTail)
            if (s.id.value > curTail.id.value) {
                tail.compareAndSet(curTail, s)
            }

            if (s.cas((newId % SEGMENT_SIZE).toInt(), null, element)) {
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
            val idx = deqIdx.getAndAdd(1)
            val s = findSegment(idx / SEGMENT_SIZE, curHead)
            if (s.id.value > curHead.id.value) {
                head.compareAndSet(curHead, s)
            }
            if (s.cas((idx % SEGMENT_SIZE).toInt(), null, DONE)) {
                continue
            }
            return s.get((idx % SEGMENT_SIZE).toInt()) as E
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
//    fun isEmpty(): Boolean
//    {
//        while (true) {
//            val head = head.value
//            if (head.isEmpty()) {
//                val headNext = head.next.value
//                if (headNext == null) return true
//                this.head.compareAndSet(head, headNext)
//                continue
//            } else {
//                return false
//            }
//        }
//    }

    val isEmpty: Boolean
        get() {
            if (deqIdx.value >= enqIdx.value) {
                return true
            }
            return false
        }

    private fun findSegment(id: Long, f: Segment): Segment {
        var s = f
        for (i in f.id.value until id) {
            val next = s.next.value

            if (next == null) {
                val newSeg = Segment()
                newSeg.setId(id + 1)
                s.next.compareAndSet(null, newSeg)
            }

            s = s.next.value!!
        }
        return s
    }
}

private class Segment {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)
    val id = atomic(0L)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }

    fun setId(newId: Long) {
//        val v = id.value
        id.compareAndSet(id.value, newId)
    }
}

private val DONE = Any()
const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

