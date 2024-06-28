package mpp.stack

import kotlinx.atomicfu.atomic

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        do {
            val localTop = top.value
            val newTop = Node(x, localTop)
        } while (!top.compareAndSet(localTop, newTop))
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        var localTop: Node<E>?
        do {
            localTop = top.value
            val newTop = localTop?.next ?: return null
        } while (!top.compareAndSet(localTop, newTop))
        return localTop?.x
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT