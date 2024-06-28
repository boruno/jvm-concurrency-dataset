package mpp.stack

import kotlinx.atomicfu.atomic
import java.util.*

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true) {
            val oldTop = top.value
            val newTop = Node(x, oldTop)
            if (setTop(oldTop, newTop)) {
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
            val oldTop = top.value ?: throw EmptyStackException()
            val newTop = oldTop.next
            if (setTop(oldTop, newTop))
                return oldTop.x
        }
    }

    private fun setTop(oldValue: Node<E>?, newValue: Node<E>?): Boolean {
        return top.compareAndSet(oldValue, newValue);
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT