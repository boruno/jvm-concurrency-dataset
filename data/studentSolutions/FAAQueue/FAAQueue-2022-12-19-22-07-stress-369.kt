package mpp.faaqueue

import kotlinx.atomicfu.*

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
            val idx = enqIdx.getAndAdd(1)
            if (idx % SEGMENT_SIZE == 0L) {
                val newTail = Segment()
                val curTail = tail.value
                if (curTail.next.compareAndSet(null, newTail)) {
                    tail.compareAndSet(curTail, newTail)
                } else {
                    tail.compareAndSet(curTail, curTail.next.value!!)
                }
            }
            if (tail.value.cas((idx % SEGMENT_SIZE).toInt(), null, element)){
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
            if (isEmpty) return null
            val idx = deqIdx.getAndAdd(1)
            if (idx % SEGMENT_SIZE == 0L) {
                val curHead = head.value
                val nextHead = curHead.next.value?: return null
                head.compareAndSet(curHead, nextHead)
            }

            val curHead = head.value
//            while(curHead.get((idx % SEGMENT_SIZE).toInt()) == null) {}
            if (curHead.cas((idx % SEGMENT_SIZE).toInt(), null, false)) continue
            try {
                if (curHead.get((idx % SEGMENT_SIZE).toInt())!!.equals(false)) continue
            } catch (e: Exception) {
                continue
            }

            return curHead.get((idx % SEGMENT_SIZE).toInt()) as? E
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

private class Segment {
    val next = atomic<Segment?>(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

