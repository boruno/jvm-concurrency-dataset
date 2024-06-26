package mpp.stack

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

            if (compareAndSet(currentTop, newTop))
                return
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

            if (compareAndSet(currentTop, newTop))
                return currentTop.x
        }
    }

    private fun compareAndSet(currentTop: Node<E>?, newTop: Node<E>?): Boolean {
        if (currentTop?.x == top.value?.x)
        {
            top.value = newTop
            return true
        }

        return false
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT