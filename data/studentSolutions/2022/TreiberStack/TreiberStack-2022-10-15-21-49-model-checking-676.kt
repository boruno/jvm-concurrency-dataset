package mpp.stack

import kotlinx.atomicfu.atomic

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    /**
     * Adds the specified element [x] to the stack.
     */
    @Synchronized
    fun push(x: E) {
        if (top.value == null) {
            top.value = Node(x, null)
        } else {
            top.value = Node(x, top.value)
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    @Synchronized
    fun pop(): E? {
        if (top.value == null) {
            return null
        } else {
            val result = top.value!!.x
            top.value = top.value!!.next
            return result
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT