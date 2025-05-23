//package day2

import Queue
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size segments.
class FAABasedQueue<E> : Queue<E> {
    private val head: AtomicRef<Node>
    private val tail: AtomicRef<Node>

    private class Node(val id: Int) {
        val segment = atomicArrayOfNulls<Any?>(SEGMENT_SIZE)
        val next = atomic<Node?>(null)
    }

    init {
        val dummy = Node(0)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        while (true) {
            var curTail = tail.value
            var i = enqIdx.incrementAndGet()
            var segmentNode = findSegment(curTail, i / SEGMENT_SIZE)
            tail.compareAndSet(curTail, segmentNode)
            if (segmentNode.segment[i % SEGMENT_SIZE].compareAndSet(null, element)) {
                return
            }
        }
    }

    private fun findSegment(start: Node, id: Int): Node {
        var curNode: Node = start
        while (true) {
            var nextNode: Node? = curNode.next.value
            while (nextNode != null) {
                if (curNode.id == id) return curNode
                curNode = nextNode
                nextNode = curNode.next.value
            }
            val newNode = Node(curNode.id + 1)
            curNode.next.compareAndSet(null, newNode)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (deqIdx.value >= enqIdx.value) return null
            var curHead = head.value
            var i = deqIdx.getAndIncrement()
            var segmentNode = findSegment(curHead, i / SEGMENT_SIZE)
            head.compareAndSet(curHead, segmentNode)
            if (!segmentNode.segment[i % SEGMENT_SIZE].compareAndSet(null, POISONED)) {
                return segmentNode.segment[i % SEGMENT_SIZE].value as E
            }
        }
    }
}

private val POISONED = Any()
private const val SEGMENT_SIZE = 4