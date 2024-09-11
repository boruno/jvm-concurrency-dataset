package mpp.stack

import kotlinx.atomicfu.atomic

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true) {
            var cur_top = top.value
            var new_top = if (cur_top == null) Node(x, null)  else Node(x, cur_top.next)

            if (top.compareAndSet(cur_top, new_top)) {
                return;
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
            var cur_top = top.value;
            if (cur_top == null) {
                return null
            }
            var newTop = cur_top.next
            if (top.compareAndSet(cur_top, newTop)) {
                return cur_top.x
            }
        }
    }
}

private class Node<E>(val x: E, var next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT