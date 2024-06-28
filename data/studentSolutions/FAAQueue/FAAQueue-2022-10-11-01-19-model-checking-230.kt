package mpp.faaqueue

import kotlinx.atomicfu.*

/**
 * @author :Цветков Николай
 */

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

    private fun findSegment(start: Segment, index: Int, segmentSize: Int = SEGMENT_SIZE): Segment {
        var cur = start
        val segIndex = index / SEGMENT_SIZE
        repeat(segIndex.toInt()) {
            if (cur.next == null) {
                cur.next = Segment()
            }
            cur = cur.next!!
        }
        return cur
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val index = enqIdx.getAndIncrement()
            var segment = findSegment(curTail, index.toInt())
            if (index > enqIdx.value)
            {
                while (true) {
                    if (tail.compareAndSet(curTail, segment)) {
                        break;
                    }
                }
            }
            if (segment.cas((index % SEGMENT_SIZE).toInt(),null, element)) {
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
            if (deqIdx.value <= enqIdx.value) {
                return null
            }
            val curHead = head.value
            val index = enqIdx.getAndIncrement()
            var segment = findSegment(curHead, index.toInt())
            if (index > deqIdx.value)
            {
                while (true) {
                    if (head.compareAndSet(curHead, segment)) {
                        break;
                    }
                }
            }
            if (segment.cas((index % SEGMENT_SIZE).toInt(),null, '⊥')) {
                continue
            }
            return segment.get((index % SEGMENT_SIZE).toInt()) as E
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            while(true) {
                val curTail = tail.value;
                if (curTail.next == null)
                {
                    break;
                }
                tail.compareAndSet(curTail, curTail.next as Segment)
            }
            return tail.compareAndSet(head.value, tail.value)
        }
}

private class Segment {
    var next: Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS