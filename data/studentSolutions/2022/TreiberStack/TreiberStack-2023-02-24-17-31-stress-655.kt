package mpp.stack

import kotlinx.atomicfu.atomic

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        var newTop = Node(x, top.value)
        while (true) {
            val oldTop = top.value
            if (top.compareAndSet(oldTop, newTop)) {
                return
            } else {
                newTop.next = oldTop
            }
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        while (true) {
            var oldTop = top.value
            if (oldTop == null) {
                return null
            }
            var newTop = oldTop.next
            if (top.compareAndSet(oldTop, newTop)) {
                return oldTop.x
            }
        }
    }
}

private class Node<E>(val x: E, var next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT