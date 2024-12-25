//@file:Suppress("UNCHECKED_CAST")

@file:Suppress("UNCHECKED_CAST")

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
            val currentTail = tail.value
            val i = enqIdx.getAndIncrement()

            val segment: Segment = findSegment(currentTail, i.toInt())

            tail.compareAndSet(currentTail, segment)

            if (segment.cas(i.toInt() % SEGMENT_SIZE, null, element)) {
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

            val i = deqIdx.getAndIncrement()
            val currentHead = head.value

            val segment: Segment = findSegment(currentHead, i.toInt())

            head.compareAndSet(currentHead, segment)

            val res = segment.getAndSet(i.toInt() % SEGMENT_SIZE, Any()) ?: continue
            return res as? E
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return enqIdx.value <= deqIdx.value
        }

    private fun findSegment(start: Segment, index: Int): Segment {
        val id = index / SEGMENT_SIZE
        var startSegment = start

        while (startSegment.getId() < id) {
            val nextSegment = startSegment.next.value
            if (nextSegment != null) {
                startSegment = nextSegment
            } else {
                val newSegment = Segment(startSegment.getId() + 1)

                if (startSegment.next.compareAndSet(null, newSegment)) {
                    startSegment = newSegment
                }
            }
        }
        return startSegment
    }
}

class Segment {
    val id: AtomicInt = atomic(0)
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun getId(): Int {
        return id.value
    }

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)

    fun getAndSet(i: Int, value: Any?) = elements[i].getAndSet(value)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }

    constructor(id: Int) {
        this.id.value = id
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

