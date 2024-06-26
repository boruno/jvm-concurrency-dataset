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
        val firstNode = Segment<Node<E>>()
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
            val segIdx = (idx % SEGMENT_SIZE).toInt()
            val seg = curTail
            if (segIdx == SEGMENT_SIZE - 1) {
                val nextSeg = Segment<Node<E>>()
                seg.next = nextSeg
                tail.value = nextSeg
            }
            // moveTailForward(seg)
            if (seg.cas(segIdx, null, Node(element))) return
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (isEmpty) return null
            val curHead = head.value
            val idx = deqIdx.getAndIncrement()
            val segIdx = (idx % SEGMENT_SIZE).toInt()
            val seg = curHead
            if (segIdx == SEGMENT_SIZE - 1) {
                head.value = seg.next!!
            }
            if (seg.cas(segIdx, null, broken)) continue
            return seg[segIdx]!!.element
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() = deqIdx.value >= enqIdx.value
}

private class Segment<E> {
    var next: Segment<E>? = null
    val elements = atomicArrayOfNulls<E>(SEGMENT_SIZE)

    operator fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: E?, update: E?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: E?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS
