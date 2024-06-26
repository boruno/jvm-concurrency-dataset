package mpp.stack

import kotlinx.atomicfu.atomic

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val newHead = Node<E>(x, null)
        val currentHead = top.value

        while (!top.compareAndSet(currentHead, newHead)) {
            newHead.next = currentHead
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val currentHead = top.value
        var newHead: Node<E>?

        if (currentHead != null) {
            newHead = currentHead.next
        } else {
            return null
        }

        while (!top.compareAndSet(currentHead, newHead)) {
            newHead = currentHead.next
        }
        return currentHead.x
    }
}

private class Node<E>(val x: E, var next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT