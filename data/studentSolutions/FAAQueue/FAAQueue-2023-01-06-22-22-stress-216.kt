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
            val currentTail = tail.value

            val i = enqIdx.getAndIncrement()

            if (i >= SEGMENT_SIZE) {
                val newTail = Segment()

                if (currentTail.next.compareAndSet(null, newTail)) {
                    currentTail.next.value!!.elements[0].value = element
                    tail.compareAndSet(currentTail, newTail)
                    return
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
            if (deqIdx.value >= enqIdx.value) { return null }

            val currentHead = head.value

            val i = deqIdx.getAndIncrement()

            if (i >= SEGMENT_SIZE) {
//                if (currentHead.next!!.elements[0].value != null) {
                    head.compareAndSet(currentHead, currentHead.next.value!!)
//                } else {
//                    return null
//                }
            } else {
                val value = currentHead.get(i.toInt())
                if (value != null) {
                    return value as E?
                }
            }
        }
//        while (true) {
////            if (deqIdx.value.toInt() >= enqIdx.value) { return null }
//
//            val headValue = head.value
//            deqIdx = atomic(deqIdx.getAndIncrement())
//
//            if (deqIdx.value >= SEGMENT_SIZE) {
//                if (headValue.next!!.elements[0].value != null) {
//                    head.compareAndSet(headValue, headValue.next!!)
//                } else {
//                    return null
//                }
//            } else {
//                val value = headValue.get(deqIdx.value.toInt())
//                if (value != null) {
//                    return value as E?
//                }
//            }
////            if (head.value.elements[deqIdx.value.toInt()].compareAndSet(null, ))
//        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            while (true) {
                if (deqIdx.value == enqIdx.value) { return true }
            }
//            while (true) {
//                val headValue = head.value
//
//                if (deqIdx.value >= enqIdx.value || deqIdx.value >= SEGMENT_SIZE) {
//                    val headValueNext = headValue.next
//                    if (headValueNext != null) {
//                        head.compareAndSet(headValue, headValueNext)
//                    } else {
//                        return true
//                    }
//                } else {
//                    return false
//                }
//            }
        }
}

private class Segment {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

