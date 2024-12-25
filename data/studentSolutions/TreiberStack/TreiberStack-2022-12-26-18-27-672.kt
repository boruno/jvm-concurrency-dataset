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
            val newTop = Node(x, currentTop)
            if (compareAndSet(currentTop, newTop)) return
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        while (true) {
            val currentTop = top.value ?: return null
            val newTop = currentTop.next
            if (compareAndSet(currentTop, newTop)) {
                return currentTop.x
            }
        }
    }

    private fun compareAndSet(old: Node<E>?, new: Node<E>?): Boolean {
        if (top.value !== old) return false
        top.value = new
        return true
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT