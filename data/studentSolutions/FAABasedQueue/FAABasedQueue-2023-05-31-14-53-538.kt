//package day2

import day1.*
import kotlinx.atomicfu.*

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size segments.
class FAABasedQueue<E> : Queue<E> {

    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    class Node<E>(idx: Int) {
        val nodeIdx = idx
        val next = atomic<Node<E>?>(null)
        val array = atomicArrayOfNulls<Any>(CHUNK_SIZE)
    }

    private val head : AtomicRef<Node<E>>
    private val tail : AtomicRef<Node<E>>
    init {
        val dummy = Node<E>(0)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    private fun moveTail(nodeIndex: Int)  {
        while (true) {
            val curTail = tail.value
            if (curTail.nodeIdx < nodeIndex) {
                val newNode = Node<E>(curTail.nodeIdx + 1)
                if (curTail.next.compareAndSet(null, newNode)) {
                    tail.compareAndSet(curTail, newNode)
                }
                else {
                    tail.compareAndSet(curTail, curTail.next.value!!)
                }
            }
            else return
        }
    }

    private fun moveHead(nodeIndex: Int) {
        while (true) {
            val curHead = head.value
            if (curHead.nodeIdx < nodeIndex) {
                val newNode = Node<E>(curHead.nodeIdx + 1)
                if (curHead.next.compareAndSet(null, newNode)) {
                    head.compareAndSet(curHead, newNode)
                }
                else {
                    head.compareAndSet(curHead, curHead.next.value!!)
                }
            }
            else return
        }
    }

    override fun enqueue(element: E) {
        while (true) {
            var curTail = tail.value
            val index = enqIdx.getAndIncrement()

            val arrIndex = index % CHUNK_SIZE
            val nodeIndex = index / CHUNK_SIZE

            moveTail(nodeIndex)
            while (curTail.nodeIdx != nodeIndex) {
                curTail = curTail.next.value!!
            }
            val s = curTail

            if (s.array[arrIndex].compareAndSet(null, element)) {
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (deqIdx.value >= enqIdx.value) {
                return null
            }
            var curHead = head.value
            val index = deqIdx.getAndIncrement()

            val arrIndex = index % CHUNK_SIZE
            val nodeIndex = index / CHUNK_SIZE

            moveHead(nodeIndex)

            while (curHead.nodeIdx != nodeIndex) {
                curHead = curHead.next.value!!
            }

            val s = curHead

            if (s.array[arrIndex].compareAndSet(BROKEN, null))
                continue

            return s.array[arrIndex].value as E?
        }
    }

    companion object {
        const val CHUNK_SIZE = 4
        val BROKEN = Any()
    }
}
