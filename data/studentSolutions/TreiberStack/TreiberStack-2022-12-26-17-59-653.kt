//package mpp.stack

import kotlinx.atomicfu.atomic

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true) {
            val head = top.value
            val newHead = Node(x, head)
            if (compareAndSet(head, newHead)) {
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
            val head = top.value
            if (compareAndSet(head, head?.next)) {
                return head?.x
            }
        }
    }

    private fun compareAndSet(old: Node<E>?, new: Node<E>?): Boolean {
        if (top.value?.equals(old) == false) {
            return false
        }
        top.value = new
        return true
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT