//package mpp.stack

import kotlinx.atomicfu.atomic

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        // while (true) {
            val node = top.value
            // top.value = Node<E>(x, node)
            if (top.compareAndSet(node, Node<E>(x, node)))
                return
        // }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        while (true) {
            val node = top.value
            if (node == null) {
                return null
            } else {
                // top.value = node.next
                if (top.compareAndSet(node, node.next))
                    return node.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT