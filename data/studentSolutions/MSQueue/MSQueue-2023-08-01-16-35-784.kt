//package day1

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
		val node = Node(element)
		while(true) {
			val curTail = tail.value
			if (curTail.next.compareAndSet(null, node)) {
				tail.compareAndSet(curTail, node)
				return
			}
			else {
				tail.compareAndSet(curTail, curTail.next.value!!)
			}
		}
    }

    override fun dequeue(): E? {
		while (true) {
			val curHead = head.value
			val newHead = curHead.next.value
			if (newHead == null) {
				return null
			}

			if (head.compareAndSet(curHead, newHead)) {
				return curHead.element
			}
		}
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
