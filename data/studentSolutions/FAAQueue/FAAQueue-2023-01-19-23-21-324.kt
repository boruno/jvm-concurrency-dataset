package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<T> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
//    val enqIdx = atomic(0L)
    val deqIdx = atomic(0L)

    init {
        val firstNode = Segment()
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(x: T) {
        while (true) {
            val tailVal = tail.value
            val tailNextVal = tailVal.next.value
            if (tailNextVal != null) {
                tail.compareAndSet(tailVal, tailNextVal)
                continue
            }
            val enqIdx1 = tailVal.enqIdx.getAndIncrement()
            if (SEGMENT_SIZE <= enqIdx1) {
//                enqIdx.incrementAndGet()
                if (tail.value.next.compareAndSet(null, Segment(x))) return
            } else {
                if (tailVal.elements[enqIdx1.toInt()].compareAndSet(null, x)) return
            }
        }
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */

    fun dequeue(): T? {
        while (true) {
            val headVal = head.value
            val deqIdx = deqIdx.getAndIncrement()
            if (deqIdx >= SEGMENT_SIZE) {
                val headNext = headVal.next.value ?: return null
                head.compareAndSet(headVal, headNext)
                continue
            }
            val res = headVal.elements[deqIdx.toInt()].getAndSet(Any())
            if (res != null) return res as T?
        }
    }

    /**
     * Returns `true` if this queue is empty;
     * `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return head.value.next.value == null
        }
}

private class Segment {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)
    val enqIdx = atomic(0L)

    constructor() // for the first segment creation

    constructor(x: Any?) { // each next new segment should be constructed with an element
        enqIdx.incrementAndGet()
        elements[0].getAndSet(x)
    }

//    fun get(i: Int) = elements[i].value
    fun cas(i: Int, el: Any?) = elements[i].getAndSet(el)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

