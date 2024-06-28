package mpp.stack

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true) {
            val topSnapshot = top
            val newTop = Node(x, topSnapshot.value)
            if (top.compareAndSet(topSnapshot.value, newTop)) {
                return
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
            val topSnapshot = top
            if (topSnapshot.value == null) {
                return null
            }
            var newTop: Node<E>? = null
            if (topSnapshot.value != null) {
                newTop = topSnapshot.value!!.next
            }
            if (top.compareAndSet(topSnapshot.value, newTop)) {
                return topSnapshot.value!!.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT