//@file:Suppress("UNCHECKED_CAST")

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
//            if (isEmpty) {
//                enqIdx.value = 0
//                deqIdx.value = 0
//                println("enq")
//                println(deqIdx.value.toInt() * 1000000 + enqIdx.value.toInt())
//            }

            val currentTail = tail.value

            val i = enqIdx.getAndIncrement()

            if (i >= SEGMENT_SIZE) {
                val newTail = Segment()

                val currentTailNext = currentTail.next.value

                if (currentTailNext == null) {
                    newTail.elements[0].value = element
                    currentTail.next.value = newTail
                    if (tail.compareAndSet(currentTail, newTail)) {
                        enqIdx.value = 1
                        return
                    }
                }
            } else if (tail.value.elements[i.toInt()].compareAndSet(null, element)) { return }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (isEmpty) {
//                enqIdx.value = 0
//                deqIdx.value = 0
//                println(enqIdx.value)
//                println(deqIdx.value)
                return -202 as E? }

            val currentHead = head.value

            val i = deqIdx.getAndIncrement()

            if (i >= SEGMENT_SIZE) {
                val currentHeadNext = currentHead.next.value
                if (currentHeadNext != null) {
                    if (head.compareAndSet(currentHead, currentHeadNext)) {
                        deqIdx.value = i % SEGMENT_SIZE
                    }
                } else {
                    return -101 as E?
                }
            } else {
                val value = currentHead.elements[i.toInt()].getAndSet(null)
                if (value != null) {
                    return value as E?
                }
            }
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            while (true) {
                val currentHead = head.value

                if (head.value == tail.value && deqIdx.value >= enqIdx.value) {
                    val currentHeadNext = currentHead.next.value
                    if (currentHeadNext != null) {
                        head.compareAndSet(currentHead, currentHeadNext)
                    } else {
                        return true
                    }
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
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

