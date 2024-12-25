//package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

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
            val tail = tail.value
            enqIdx.getAndAdd(1)

            if (enqIdx.value >= SEGMENT_SIZE) {
                val newTail = Segment(element)
                if (tail.next.compareAndSet(null, newTail)) {
                    this.tail.compareAndSet(tail, newTail)
                    break
                } else {
                    this.tail.compareAndSet(tail, tail.next.value!!)
                    continue
                }
            } else if (tail.elements[enqIdx.value].compareAndSet(null, element)) {
                break
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
            deqIdx.getAndAdd(1)

            if (deqIdx.value >= SEGMENT_SIZE) {
                val nextHead = head.next.value ?: return null
                this.head.compareAndSet(head, nextHead)
            } else {
                return head.elements[deqIdx.value].getAndSet(DONE) as E? ?: continue
            }
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            while (true) {
                val head = head.value
                val nextHead = head.next.value

                if (deqIdx.value >= enqIdx.value || deqIdx.value >= SEGMENT_SIZE) {
                    if (nextHead == null) {
                        return true
                    } else {
                        this.head.compareAndSet(head, nextHead)
                    }
                } else {
                    return false
                }
            }
        }
}

private class Segment {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    constructor()
    constructor(element: Any?) {
        elements[0].getAndSet(element)
    }

    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

private val DONE = Any()
const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS
