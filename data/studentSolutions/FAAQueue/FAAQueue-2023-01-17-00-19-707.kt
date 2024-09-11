package mpp.faaqueue

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

val BREAK = Any()

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
     * Adds the specified element [element] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            var curTail = tail.value
            val i = enqIdx.getAndIncrement()
            //var curTail = findSegment(i, 0)
            var s = findSegment(curTail, i, 1)
//            if (s.id > tail.value.id) {
//                while(true) {
//                    curTail = tail.value
//                    if (tail.compareAndSet(curTail, s)) {
//                        break
//                    }
//                }
//            }
            if (s.cas((i % SEGMENT_SIZE).toInt(), null, element)) {
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
            if (enqIdx.value <= deqIdx.value) {
                return null
            }
            var curHead = head.value
            val i = deqIdx.getAndIncrement()

            //var curHead = findSegment(i, 1)
            var s = findSegment(curHead, i, 1) ?: continue
//            if (s.id > head.value.id) {
//                while(true) {
//                    curHead = head.value
//                    if (head.compareAndSet(curHead, s)) {
//                        break
//                    }
//                }
//            }
            if (s.cas((i % SEGMENT_SIZE).toInt(), null, BREAK)) {
                continue
            }
            return s.get((i % SEGMENT_SIZE).toInt()) as E?
        }
    }

    private fun findSegment(s: Segment, i: Long, isHead : Int): Segment {
        var start: Segment = s
//        if (isHead == 1) {
//            start = head.value
//        }
//        else start = tail.value
        while (start.id < (i / SEGMENT_SIZE).toInt()) {
            var next = start.next
            if (next == null) {
                next = Segment()
                next.id = start.id + 1
                start.next = next
            }
            if (isHead == 1) {
                if (head.compareAndSet(start, next)) {
                    start = next
                } else start = head.value
            } else {
                if (tail.compareAndSet(start, next)) {
                    start = next
                } else start = tail.value
            }
//            val new = next ?: Segment()
//            new.id = start.id + 1
//            if (isHead == 1) {
//                if (head.compareAndSet(start, new)) {
//                    start.next = new
//                    start = new
//                    //return new
//                } else start = head.value
//            } else {
//                if (tail.compareAndSet(start, new)) {
//                    start.next = new
//                    start = new
//                    //return new
//                } else start = tail.value
//            }

        }
        return start

    }


    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            if (deqIdx.value <= enqIdx.value) {
                return true
            }
            return false
        }
}

class Segment {
    var next: Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)
    var id: Int = 0
    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

