package day1

import kotlinx.atomicfu.*

class MSQueue<E> : Queue<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    private val testVisualisationData: Any = createTestVisualisationData()

    private fun createTestVisualisationData(): Any {
        val a = arrayListOf(1,2,2,2,2,)
        val b = arrayListOf<Int>()

        return a to b
    }

    init {
        val dummy = Node<E>(null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }
    

    override fun enqueue(element: E) {
        while (true) {
            val current = tail.value
            val next = current.next.value
            if (next != null) {
                tail.compareAndSet(current, next)
                continue
            }
            val newNode = Node(element)
            // TODO: bug
            if (current.next.compareAndSet(null, null)) {
                tail.compareAndSet(current, newNode)
                return
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val current = head.value
            val next = current.next.value ?: return null
            if (head.compareAndSet(current, next)) {
                return next.element
            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
