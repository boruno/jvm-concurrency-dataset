package day1

import kotlinx.atomicfu.*

class MSQueue<E> : Queue<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    override fun enqueue(element: E) {
        val newNode = Node(element)
        while (true) {
            val curTail = tail.value
            val next = curTail.next.value
            newNode.next.value = next
            if (tail.value == curTail) {
                if (next == null) {
                    if (tail.value.next.compareAndSet(null, newNode)) break
                }
                else {
                    tail.compareAndSet(curTail, next)
                }
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val curHead = head.value
            val curTail = tail.value
            val next = curTail.next.value
            if (curHead == head.value) {
                if (curHead == curTail) {
                    if (next == null) return null
                    tail.compareAndSet(curTail, next)
                } else {
                    if (head.compareAndSet(curHead, next!!)) {
                        val item = next.element
                        next.element = null // help to gc
                        return item
                    }
                }
            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
