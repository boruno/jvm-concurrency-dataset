//package mpp.faaqueue

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
            val curTail = tail.value
            val idx = enqIdx.getAndIncrement()

            if (idx >= SEGMENT_SIZE) {
                val newTail = Segment()
                newTail.put(0, element)
//                enqIdx.getAndIncrement()

                if (curTail.next?.cas(idx.toInt() % SEGMENT_SIZE, null, newTail)!!) {
                    tail.compareAndSet(curTail, newTail)
                    return
                }

                curTail.next?.let { tail.compareAndSet(curTail, it) }
            } else {
                if (curTail.elements[idx.toInt() % SEGMENT_SIZE].compareAndSet(null, element)) {
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
            if (deqIdx.value <= enqIdx.value) {
                return null
            }

            val curHead = head.value
            val idx = deqIdx.getAndIncrement()

            if (idx >= SEGMENT_SIZE) {
                val nextHead = curHead.next ?: return null
                head.compareAndSet(curHead, nextHead)
                continue
            }

            val res = curHead.elements[idx.toInt() % SEGMENT_SIZE].getAndSet(BREAK) ?: continue
            return res as E?
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            while (true) {
                val curHead = head.value

                val isEmpty = deqIdx.value >= enqIdx.value || deqIdx.value >= SEGMENT_SIZE

                if (isEmpty) {
                    val nextHead = curHead.next ?: return true
                    head.compareAndSet(curHead, nextHead)
                    continue
                }

                return false
            }
        }
}

private class Segment {
    var next: Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    private fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS
private val BREAK = Any()

