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
            val tail = this.tail.value
            enqIdx.getAndIncrement()
            if (enqIdx.value < SEGMENT_SIZE) {
                if (tail.cas(enqIdx.value.toInt(),null, element)) {
                    return
                }
            } else {
                val newT = Segment()
                newT.put(0, element)
                if (tail.next!!.cas(1, null, newT)) {
                    this.tail.compareAndSet(tail, newT)
                    return
                } else {
                    this.tail.compareAndSet(tail, tail.next!!)
                }
            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            val head = head.value
            deqIdx.getAndIncrement()
            if (deqIdx.value >= SEGMENT_SIZE) {
                val headN = head.next ?: return null
                this.head.compareAndSet(head, headN)
                continue
            }
            return head.get(deqIdx.value.toInt()) as E?
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            while (true) {
                val head = this.head.value
                if (deqIdx.value >= enqIdx.value || deqIdx.value >= SEGMENT_SIZE) {
                    val headN = head.next ?: return true
                    this.head.compareAndSet(head, headN)
                    continue
                } else {
                    return false
                }
            }
        }
}

private class Segment {

    var next: Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].getAndSet(value)
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

