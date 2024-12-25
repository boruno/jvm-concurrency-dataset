//package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment(1)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            println("try to add $element")
            var curTail = tail.value
            val curEnqIdx = enqIdx.getAndIncrement()
            while (curTail.id < (curEnqIdx + 1) / SEGMENT_SIZE) {
                curTail = curTail.next.value ?: return
            }
            if (curEnqIdx + 1 == curTail.id.toLong() * SEGMENT_SIZE) {
                val newTail = Segment(element, curTail.id + 1)
                if (curTail.next.compareAndSet(null, newTail)) {
                    tail.compareAndSet(curTail, newTail)
                    break
                }
            } else {
                if (curTail.cas(((curEnqIdx + 1) % SEGMENT_SIZE).toInt(),
                        null, element))
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
            println("try to deque")
            if (deqIdx.value <= enqIdx.value) return null
            var curHead = head.value
            val curDeqIdx = deqIdx.getAndIncrement()
            while (curHead.id < (curDeqIdx + 1) / SEGMENT_SIZE) {
                curHead = curHead.next.value ?: return null
            }
            val i = (curDeqIdx % SEGMENT_SIZE).toInt()
            if (curHead.cas(i, null, Any()))
                continue
            if (i == SEGMENT_SIZE - 1) {
                val newHead = curHead.next.value ?: return null
                if (head.compareAndSet(curHead, newHead)) {
                    return curHead.get(i) as E?
                }
            }
            return curHead.get(i) as E?
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.value <= enqIdx.value
        }
}

private class Segment(val id: Int) {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    constructor(x: Any?, id: Int) : this(id) {
        put(0, x)
    }

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE: Int = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

