package mpp.stack

import kotlinx.atomicfu.atomic

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        var oldHead: Node<E>
        var newHead: Node<E>
        while (true) {
            oldHead = top.value!!
            newHead = Node(x, oldHead)
            if (top.compareAndSet(oldHead, newHead)) {
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
        var oldHead: Node<E>
        while(true) {
            val a = top.value
            if (a != null) {
                oldHead = a
            } else {
                return null
            }
            if (top.compareAndSet(oldHead, oldHead.next)) {
                return oldHead.x
            }
        }
    }
}

private class Node<E>(val x: E, var next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT