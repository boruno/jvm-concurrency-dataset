//package mpp.stack

import kotlinx.atomicfu.atomic

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        do {
            val old_top = top
            val new_top = Node<E>(x, old_top.value)
        } while (!top.compareAndSet(old_top.value, new_top))
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        while (true) {
            var old_top = top.value
            if (old_top == null) {
                return null
            }
            if (top.compareAndSet(old_top, old_top.next)) {
                return old_top.value
            }
        }
    }
}

private class Node<E>(val x: E, val n: Node<E>?) {
    var value = x
    var next = n
}

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT