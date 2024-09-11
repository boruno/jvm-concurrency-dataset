package mpp.faaqueue

import kotlinx.atomicfu.*

private data class Node<E>(val element: E? = null, val isBroken: Boolean = false)

class FAAQueue<E> {

    private val head: AtomicRef<Segment<Node<E>>>
    private val tail: AtomicRef<Segment<Node<E>>>
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)
    private val broken = Node<E>(null, true)

    init {
        val firstNode = Segment<Node<E>>(0)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val seg = tail.value
            if (seg.next.value == null) {
                val nextSeg = Segment<Node<E>>(seg.index + 1)
                seg.next.compareAndSet(null, nextSeg)
            }

            val idx = enqIdx.getAndIncrement()
            val segIdx = (idx / SEGMENT_SIZE).toInt()
            if (segIdx != seg.index) {
                tail.compareAndSet(seg, seg.next.value!!)
                continue
            }

            val arrIdx = (idx % SEGMENT_SIZE).toInt()
            if (seg.cas(arrIdx, null, Node(element))) return
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (isEmpty) return null
            var seg = head.value
            val idx = deqIdx.getAndIncrement()
            val segIdx = (idx / SEGMENT_SIZE).toInt()
            while (segIdx != seg.index) {
                val nextSeg = seg.next.value!!
                head.compareAndSet(seg, nextSeg)
                seg = nextSeg
            }

            val arrIdx = (idx % SEGMENT_SIZE).toInt()
            if (seg.cas(arrIdx, null, broken)) continue
            return seg[arrIdx]!!.element
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

private class Segment<T>(val index: Int) {
    val next = atomic<Segment<T>?>(null)
    val elements = atomicArrayOfNulls<T>(SEGMENT_SIZE)

    operator fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: T?, update: T?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: T?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS
