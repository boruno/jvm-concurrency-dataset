package day2

import day1.Queue
import kotlinx.atomicfu.AtomicArray
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size segments.
class FAABasedQueue<E> : Queue<E> {
    private val head: AtomicRef<Node>
    private val tail: AtomicRef<Node>
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    init {
        val dummy = Node(0)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    override fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val ind = enqIdx.getAndIncrement()
            val segment = findSegment(curTail, id = ind / SEGM_SIZE)
            if (curTail != segment) {
                tail.compareAndSet(curTail, segment)
            }
            if (segment.array[ind % SEGM_SIZE].compareAndSet(null, element)) {
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            while (true) {
                val e1 = enqIdx.value
                val d = deqIdx.value
                val e2 = enqIdx.value
                // enqInd can be changed between first and second .value call, so here this warning is wrong
                @Suppress("KotlinConstantConditions")
                if (e1 == e2) {
                    if (d >= e2) return null
                    break
                } else continue
            }

            val curHead = head.value
            val ind = deqIdx.getAndIncrement()
            val segment = findSegment(curHead, id = ind / SEGM_SIZE)
            if (curHead != segment) {
                head.compareAndSet(curHead, segment)
            }
            if (segment.array[ind % SEGM_SIZE].compareAndSet(null, POISONED)) {
                continue
            }
            return segment.array[ind % SEGM_SIZE].value as E?
        }
    }

    private fun findSegment(start: Node, id: Int): Node {
        while (true) {
            var curNode: Node = start
            while (curNode.id != id) {
                curNode = curNode.next.value ?: break
            }
            if (curNode.id == id) {
                return curNode
            }
            val newNode = Node(id + 1)
            if (curNode.next.compareAndSet(null, newNode)) {
                return newNode
            }
        }
    }

    private class Node(
        val id: Int
    ) {
        val array: AtomicArray<Any?> = atomicArrayOfNulls(SEGM_SIZE)
        val next = atomic<Node?>(null)
    }
}

private val SEGM_SIZE = 2

private val POISONED = Any()