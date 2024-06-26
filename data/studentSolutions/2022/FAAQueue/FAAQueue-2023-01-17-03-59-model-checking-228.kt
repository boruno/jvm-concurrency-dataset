package mpp.faaqueue

import kotlinx.atomicfu.*

@Suppress("UNCHECKED_CAST")
class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue

    init {
        val node = Segment()
        head = atomic(node)
        tail = atomic(node)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val idx = curTail.enqIdx.getAndIncrement().toInt()
            if (idx < SEGMENT_SIZE) {
                if (curTail.cas(idx, null, element)) return
                else continue
            }
            val node = Segment()
            curTail.enqIdx.getAndSet(1L)
            node.getAndPut(0, element)
            if (!curTail.next.compareAndSet(null, node))
                continue
            tail.compareAndSet(curTail, curTail.next.value!!)
            return
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            val curHead = head.value
            val idx = curHead.deqIdx.getAndIncrement().toInt()
            if (idx < SEGMENT_SIZE) {
                val peek = curHead.getAndPut(idx, Any())
                if (peek != null) return peek as E
                continue
            }
            val next = curHead.next
            next.value ?: return null
            head.compareAndSet(curHead, next.value!!)
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            while (true) {
                val curHead = head.value
                if (curHead.deqIdx.value < curHead.enqIdx.value && curHead.deqIdx.value < SEGMENT_SIZE)
                    return false
                curHead.next.value ?: return true
                head.compareAndSet(curHead, curHead.next.value!!)
            }
        }
}

private class Segment {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)
    val enqIdx = atomic(0L)
    val deqIdx = atomic(0L)

    fun getAndPut(i: Int, value: Any?) = elements[i].getAndSet(value)
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS
