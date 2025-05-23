//package mpp.stack

import kotlinx.atomicfu.atomic

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true) {
            val currentTop = top.value
            val nextTop = Node(x, currentTop)
            if (top.compareAndSet(currentTop, nextTop)) return
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        while (true) {
            val currentTop = top.value ?: throw NullPointerException()
            val nextTop = currentTop.next
            if (top.compareAndSet(currentTop, nextTop)) return currentTop.x
        }
    }
}

private class Node<E>(val x: E, var next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT