//package day2

import kotlinx.atomicfu.*

class FAABasedQueue<E> : Queue<E> {
    private val head: AtomicRef<Node>
    private val tail: AtomicRef<Node>

    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    init {
        val dummy = Node(0)// conceptually infinite array
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    override fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val i = enqIdx.getAndIncrement()
            val segment = findSegment(start = curTail, id = i / SEGMENT_SIZE) ?: return
            moveTailForward(segment)
            if (segment.array[i % SEGMENT_SIZE].compareAndSet(EMPTY, element)) {
                return
            }
        }
    }

    private fun moveTailForward(segment: Node) {
        val cur = tail.value
        while (segment.id > cur.id) {
            val next = cur.next.value ?: return
            tail.compareAndSet(cur, next)
        }
    }

    private fun moveHeadForward(segment: Node) {
        val cur = head.value
        while (segment.id > cur.id) {
            val next = cur.next.value ?: return
            head.compareAndSet(cur, next)
        }
    }

    private fun findSegment(start: Node, id: Int): Node? {
        var curNode: Node = start
        while (curNode.id != id ) {
            curNode = curNode.next.value ?: return null
        }
        return curNode
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (!shouldTryDequeue()) return null
            val curHead = head.value
            val i = deqIdx.getAndIncrement()
            val segment = findSegment(start = curHead, id = i / SEGMENT_SIZE) ?: return null
            moveHeadForward(segment)
            if (!segment.array[i % SEGMENT_SIZE].compareAndSet(null, POISONED)) {
                return segment.array[i % SEGMENT_SIZE].value as E
            }
        }
    }

    private fun shouldTryDequeue(): Boolean {
        while (true) {
            val curDeqIdx = deqIdx.value
            val curEnqIdx = enqIdx.value
            if (curDeqIdx == deqIdx.value) {
                return curDeqIdx < curEnqIdx
            }
        }
    }

    private class Node(
        val id: Int
    ) {
        val array = atomicArrayOfNulls<Any?>(SEGMENT_SIZE)
        val next = atomic<Node?>(null)
    }
}


private val POISONED = Any()
private val EMPTY = null
private const val SEGMENT_SIZE = 5