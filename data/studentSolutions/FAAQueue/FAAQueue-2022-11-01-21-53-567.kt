//package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment(0L)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val currentTail = tail.value
            val index = enqIdx.getAndAdd(1)
            val currentSegmentBound = currentTail.id * SEGMENT_SIZE + (SEGMENT_SIZE - 1)

            if (index > currentSegmentBound) {
                val newSegment = Segment(currentTail.id + 1)
                newSegment.put((index % SEGMENT_SIZE).toInt(), element)

                if (currentTail.next.compareAndSet(null, newSegment)) {
                    if (tail.compareAndSet(currentTail, newSegment)) {
                        return
                    }
                }
            }

            if (currentTail.cas((index % SEGMENT_SIZE).toInt(), null, element)) {
                println("Enqueue $element at cell ${(index % SEGMENT_SIZE).toInt()} in segment ${currentTail.id}")
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
            val currentHead = head.value

            if (isEmpty) {
                if (currentHead.next.value == null)
                    println("Dequeue null: queue is empty")
                    return null
            }

            val index = deqIdx.getAndAdd(1)
            val currentSegmentBound = currentHead.id * SEGMENT_SIZE + (SEGMENT_SIZE - 1)

            if (index > currentSegmentBound) {
                val nextSegment = currentHead.next.value
                if (nextSegment == null) {
                    println("Dequeue null: next segment is null and current index is out of current head bound ($index, ${currentHead.id})")
                    return null
                }

                // We should continue anyway: to update currentHead or to try again.
                head.compareAndSet(currentHead, nextSegment)
                continue
            }

            if (currentHead.cas((index % SEGMENT_SIZE).toInt(), null, BROKEN)) {
                println("Broke cell at " + (index % SEGMENT_SIZE).toInt() + "in segment " + currentHead.id)
                continue
            }

            val element = currentHead.get((index % SEGMENT_SIZE).toInt()) as E?
            println("Dequeue element " + element + " from cell " + (index % SEGMENT_SIZE).toInt() + " in segment " + currentHead.id)
            return element
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

private class Segment(val id: Long) {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

private class BrokenCell
private val BROKEN = BrokenCell()

