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
//        TODO("implement me")
        while (true) {
            val curTail = tail.value
            val curNext = curTail.next
            if (curNext != null) {
                tail.compareAndSet(curTail, curNext)
            } else {
                val i = enqIdx.getAndIncrement()
                if (i < SEGMENT_SIZE) {
                    if (curTail.cas(i.toInt(), null, element)) {
                        return
                    }
                } else {
                    if (tail.value.next == null) {
                        tail.value.next = Segment()
                        if (tail.value.next!!.cas(0, null, element)) {
                            enqIdx.getAndSet(1)
                            return
                        }
                    }
                } // ?
            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
//        TODO("implement me")
        while (true) {
            val curHead = head.value
            val i = deqIdx.getAndIncrement()
            if (i < SEGMENT_SIZE) {
                return curHead.get(i.toInt()) as E?
            } else {
                if (curHead.next == null) {
                    return null
                }
                val headNext = curHead.next!!
                head.compareAndSet(curHead, headNext)
            }
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            while (true) {
                val ei = enqIdx.value
                val di = deqIdx.value
                if (di >= ei || di >= SEGMENT_SIZE) {
                    if (head.value.next == null) {
                        return true
                    }
                    head.value = head.value.next!!
                } else {
                    return false
                }
            }
        }
}

private class Segment {
    var next: Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value // private??
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update) // private??
    fun put(i: Int, value: Any?) { // private???
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

