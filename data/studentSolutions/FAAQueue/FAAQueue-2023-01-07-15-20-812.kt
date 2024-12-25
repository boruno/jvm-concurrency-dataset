//@file:Suppress("UNCHECKED_CAST")

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
//            if (isEmpty) {
//                enqIdx.value = 0
//                deqIdx.value = 0
//                println("enq")
//                println(deqIdx.value.toInt() * 1000000 + enqIdx.value.toInt())
//            }

            val currentTail = tail.value

            val i = enqIdx.getAndIncrement()

            val segment = findSegment(currentTail, i.toInt())

            if ((i.toInt() % SEGMENT_SIZE) == 0/*i >= SEGMENT_SIZE*/) {
                val newSegmentId = currentTail.getId() + 1

                val newTail = Segment(newSegmentId)

//                val currentTailNext = currentTail.next.value
                val currentTailNext = segment.next.value

                if (currentTailNext == null) {
                    newTail.elements[0].value = element
                    currentTail.next.value = newTail
                    if (tail.compareAndSet(currentTail, newTail)) {
//                        enqIdx.value = 1
                        return
                    }
                }
            } else if (tail.value.elements[i.toInt() % SEGMENT_SIZE].compareAndSet(null, element)) { return }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (isEmpty) {
//                enqIdx.value = 0
//                deqIdx.value = 0
//                println(enqIdx.value)
//                println(deqIdx.value)
                return null }

            val currentHead = head.value

            val i = deqIdx.getAndIncrement()

            val segment = findSegment(currentHead, i.toInt())

            if ((i.toInt() % SEGMENT_SIZE) == 0/*i >= SEGMENT_SIZE*/) {
//                val currentHeadNext = currentHead.next.value
                val currentHeadNext = segment.next.value

                if (currentHeadNext != null) {
//                    if (
                        head.compareAndSet(currentHead, currentHeadNext)
//                    ) {
//                        deqIdx.value = i % SEGMENT_SIZE
//                    }
                } else {
                    return null
                }
            } else {
                val value = currentHead.elements[i.toInt() % SEGMENT_SIZE].getAndSet(null)
                if (value != null) {
                    return value as E?
                }
            }
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            while (true) {
                val currentHead = head.value

                if (head.value == tail.value && deqIdx.value >= enqIdx.value) {
                    val currentHeadNext = currentHead.next.value
                    if (currentHeadNext != null) {
                        head.compareAndSet(currentHead, currentHeadNext)
                    } else {
                        return true
                    }
                } else {
                    return false
                }
            }
        }

    fun findSegment(start: Segment, index: Int) : Segment {
        val id = index / SEGMENT_SIZE
        var startSegment = start

        while (startSegment.getId() != id) {
            val nextSegment = startSegment.next.value
            if (nextSegment != null) {
                startSegment = nextSegment
            } else {
                return startSegment
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
    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
    constructor(id: Int) {
        this.id.value = id
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

