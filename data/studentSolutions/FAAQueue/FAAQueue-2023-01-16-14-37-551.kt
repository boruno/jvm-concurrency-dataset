//package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment<E>> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment<E>> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment<E>()
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val tailValue = tail.value
            val tailEnqIdx = tailValue.enqIdx.getAndIncrement()
            if (tailEnqIdx >= SEGMENT_SIZE) {
                val segment = Segment(element)
                if (tailValue.next.compareAndSet(null, segment)) {
                    tail.compareAndSet(tailValue, segment)
                    return
                } else {
                    tail.compareAndSet(tailValue, tailValue.next.value!!)
                    continue
                }
            }
            if (!tailValue.elements.get(tailEnqIdx).compareAndSet(null, element)) {
                continue
            }
            return
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            val headValue = head.value
            val headDeqIdx = headValue.deqIdx.getAndIncrement()
            if (headDeqIdx >= SEGMENT_SIZE) {
                if (headValue.next.value != null) {
                    head.compareAndSet(headValue, headValue.next.value!!)
                    continue
                } else {
                    return null
                }
            }
            val elem = headValue.elements.get(headDeqIdx).getAndSet(null)
            if (elem == null) {
                continue
            }
            return elem
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            while(true) {
                if (!head.value.isEmpty()) {
                    return false
                }
                if (head.value.next.value == null) {
                    return true
                }
                head.value = head.value.next.value!!
            }
    }
}

private class Segment<E> {
    val enqIdx = atomic(0)
    val deqIdx = atomic(0)
    val next: AtomicRef<Segment<E>?> = atomic(null)
    val elements = atomicArrayOfNulls<E?>(SEGMENT_SIZE)

    // private fun get(i: Int) = elements[i].value
    // private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    // private fun put(i: Int, value: Any?) {
    //     elements[i].value = value
    // }

    init {
        
    }

    constructor(element: E) {
        enqIdx.value = 1
        elements.get(0).getAndSet(element)
    }

    constructor() {
    }

    fun isEmpty(): Boolean {
        val enqIdxValue = enqIdx.value
        val deqIdxValue = deqIdx.value

        return deqIdxValue >= enqIdxValue || deqIdxValue >= SEGMENT_SIZE
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

