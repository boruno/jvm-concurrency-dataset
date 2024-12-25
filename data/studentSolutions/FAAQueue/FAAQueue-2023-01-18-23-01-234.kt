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
            val curTail = tail.value;
            val i = enqIdx.incrementAndGet();
            val s = findSegment(curTail, i);
            if (s != curTail) {
                head.compareAndSet(curTail, s)
            };
            if (s.elements[i.toInt() % SEGMENT_SIZE].compareAndSet(null, element)) {
                return
            }
        }
    }

    private fun findSegment(segment: Segment, i: Long): Segment {
        var cur = segment;
        while (cur.first + SEGMENT_SIZE - 1 < i) {
            val next = cur.next.value;
            if (next == null) {
                val newNext = Segment(cur.first + SEGMENT_SIZE);
                if (cur.next.compareAndSet(null, newNext)) {
                    cur = newNext;
                    continue;
                }
            } else {
                cur = next;
                continue;
            }
        }
        return cur;
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (isEmpty) {
                return null;
            }
            val curHead = head.value;
            val i = enqIdx.getAndIncrement();
            val s = findSegment(curHead, i);
            if (s != curHead) {
                head.compareAndSet(curHead, s)
            };
            if (s.elements[i.toInt() % SEGMENT_SIZE].compareAndSet(null, BREAK)) {
                continue;
            }
            return (s.elements[i.toInt() % SEGMENT_SIZE].value as E?);

        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.value >= enqIdx.value;
        }
}

public class Segment(l: Long) {
    var first: Long = l;
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS
val BREAK = Any();
