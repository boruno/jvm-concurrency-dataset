package day1

import kotlinx.atomicfu.*

class MSQueue<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Adds the specified [element] to the queue.
     */
    fun enqueue(element: E) {
        TODO("implement me")
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        TODO("implement me")
    }

    /**
     * Returns `true` if this queue is empty.
     */
    fun isEmpty(): Boolean {
        TODO("implement me")
    }
}

private class Node<E>(
    var element: E?
) {
    val next = atomic<Node<E>?>(null)
}
