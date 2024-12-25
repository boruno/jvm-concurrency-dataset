//package mpp.stack

import kotlinx.atomicfu.atomic

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        var newHead = Node<E>(x, null)
        var oldHead: Node<E>?
        oldHead = top.value
        while (!top.compareAndSet(oldHead, newHead)) {
            newHead.next = oldHead
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        var oldHead: Node<E>?
        var newHead: Node<E>?

        oldHead = top.value
        if (oldHead != null) {
            newHead = oldHead.next
        } else {
            return null
        }

        while (!top.compareAndSet(oldHead, newHead)) {
            newHead = oldHead.next
        }
        return oldHead.x
    }
}

private class Node<E>(val x: E, var next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT