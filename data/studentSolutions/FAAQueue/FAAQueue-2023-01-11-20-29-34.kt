//package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)
    private val tombstone = Tombstone()
    private class Tombstone

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
            val i = enqIdx.getAndIncrement()
            val j = i % SEGMENT_SIZE

            if (i >= SEGMENT_SIZE * curTail.id) { // out of segment, try to add a new one
                val nextSegment = Segment(curTail.id + 1)
                nextSegment.put(0, element)
                if (curTail.next.compareAndSet(null, nextSegment)) {
                    if (tail.compareAndSet(curTail, nextSegment)) {
                        return // success
                    }
                }
            } else if (curTail.elements[j.toInt()].compareAndSet(null, element)) {
                return // success
            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (deqIdx.value >= enqIdx.value) return null
            val curHead = head.value
            val i = deqIdx.getAndIncrement()
            val j = i % SEGMENT_SIZE

            if (i >= SEGMENT_SIZE * curHead.id) { // out of segment, go further
                head.compareAndSet(curHead, curHead.next.value!!) // roll again
            } else {
                if (curHead.cas(j.toInt(), null, tombstone)) {
                    continue // enqueue stuck, roll again
                } else {
                    return curHead.get(j.toInt()) as E? // success
                }
            }
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.value >= enqIdx.value
        }
}

private class Segment(val id: Int) {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

