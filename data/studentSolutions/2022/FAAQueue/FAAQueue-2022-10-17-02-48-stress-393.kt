package mpp.faaqueue

import kotlinx.atomicfu.*
import java.lang.Exception

class FAAQueue<E> {
    class Done

    private val done = Done()

    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue

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
            val tail = tail.value
            val next = tail.next.value
            if (next != null) {
                this.tail.compareAndSet(tail, next)
                continue
            }
            val enqIdx = tail.enqIdx.getAndIncrement()
            if (enqIdx == SEGMENT_SIZE) {
                val segment = Segment.withElement(element)
                if (this.tail.value.next.compareAndSet(null, segment)) {
                    return
                }
            } else {
                if (this.tail.value.cas(enqIdx, null, element)) {
                    return
                }
            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    @Suppress("UNCHECKED_CAST")
    fun dequeue(): E? {
        while (true) {
            val head = this.head.value
            val next = head.next.value
            val deqIdx = head.deqIdx.getAndIncrement()
            if (deqIdx == SEGMENT_SIZE) {
                if (next != null) {
                    this.head.compareAndSet(head, next)
                    continue
                }
                return null
            }
            return when (val element = head.elements[deqIdx].getAndSet(done)) {
                is Done -> null
                null -> continue
                else -> element as E
            }
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            while (true) {
                return if (head.value.isEmpty) {
                    if (head.value.next.value != null) {
                        head.value = head.value.next.value!!
                        continue
                    }
                    true
                }
                else {
                    false
                }
            }
        }
}

private class Segment {
    val next = atomic<Segment?>(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)
    val enqIdx = atomic(0)
    val deqIdx = atomic(0)

    companion object {
        fun withElement(element: Any?): Segment {
            val segment = Segment()
            segment.put(0, element)
            segment.enqIdx.incrementAndGet()
            return segment
        }
    }

    private fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
    val isEmpty: Boolean
        get() {
            val deqValue = deqIdx.value
            val enqValue = enqIdx.value
            return deqValue >= enqValue || deqValue >= SEGMENT_SIZE
        }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

