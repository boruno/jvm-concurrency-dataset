//package mpp.faaqueue

import kotlinx.atomicfu.*

@Suppress("UNCHECKED_CAST")
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

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val curEnqIdx = enqIdx.getAndIncrement()
            if (curEnqIdx >= SEGMENT_SIZE) {
                val newTail: Segment
                run {
                    newTail = Segment()
                    enqIdx.getAndSet(1)
                    newTail.put(0, element)
                }
                val flag = curTail.next.compareAndSet(null, newTail)
                tail.compareAndSet(curTail, curTail.next.value!!)
                if (flag) return
            } else {
                if (curTail.elements[curEnqIdx.toInt()].compareAndSet(null, element)) {
                    return
                }
            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            val curHead = head.value
            val curDeqIdx = deqIdx.getAndIncrement()
            if (curDeqIdx >= SEGMENT_SIZE) {
                val next = curHead.next
                if (next.compareAndSet(null, null)) return null
                next.value?.let { head.compareAndSet(curHead, it) }
            } else {
                val res = curHead.elements[curDeqIdx.toInt()].getAndSet(Any()) ?: continue
                return res as E?
            }
        }
    }

    fun checkEmpty(): Boolean {
        return deqIdx.value >= enqIdx.value || deqIdx.value >= SEGMENT_SIZE
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            while (true) {
                val curHead = head.value
                if (checkEmpty()) {
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

    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }

    fun initialize(enqIdx: AtomicLong, idx: Int, el: Any?) {
        enqIdx.getAndSet(1)
        put(idx, el)
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS
