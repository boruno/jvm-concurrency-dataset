//package mpp.faaqueue

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
            val curEnqIdx = curTail.enqIdx.getAndIncrement().toInt()
            if (curEnqIdx < SEGMENT_SIZE) {
                if (curTail.elements[curEnqIdx].compareAndSet(null, element)) return
                else continue
            }
            val newTail = Segment()
            curTail.enqIdx.getAndSet(1L)
            newTail.elements[0].getAndSet(element)
            val flag = curTail.next.compareAndSet(null, newTail)
            tail.compareAndSet(curTail, curTail.next.value!!)
            if (flag) return
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            val curHead = head.value
            val curDeqIdx = curHead.deqIdx.getAndIncrement().toInt()
            if (curDeqIdx < SEGMENT_SIZE) {
                curHead.put(curDeqIdx, DONE)
                val res = curHead.get(curDeqIdx) ?: continue
                return res as E?
            }
            val next = curHead.next
            if (next.compareAndSet(null, null)) return null
            next.value?.let { head.compareAndSet(curHead, it) }
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            while (true) {
                val curHead = head.value
                if (curHead.deqIdx.value >= curHead.enqIdx.value || curHead.deqIdx.value >= SEGMENT_SIZE) {
                    if (curHead.next.compareAndSet(null, null)) return true
                    head.compareAndSet(curHead, curHead.next.value!!)
                    continue
                } else {
                    return false
                }
            }
        }
}

private class Segment {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)
    val enqIdx = atomic(0L)
    val deqIdx = atomic(0L)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].getAndSet(value)
    }
}

private val DONE = Any()
const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS
