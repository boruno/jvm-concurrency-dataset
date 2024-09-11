package mpp.stack

import kotlinx.atomicfu.atomic

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true) {
            val curTop = top.value
            if (curTop != null) {
                val newTop: Node<E> = Node(curTop.x, Node(x, null))
                if (top.compareAndSet(curTop, newTop)) {
                    return
                }
            } else {
                if (top.compareAndSet(null, Node(x, null))) {
                    return
                }
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
            val curTop = top.value
            if (curTop != null) {
                val newTop = curTop.next
                if (top.compareAndSet(curTop, newTop)) {
                    return curTop.x
                }
            } else {
                if (top.compareAndSet(null, null)) {
                    return null
                }
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT