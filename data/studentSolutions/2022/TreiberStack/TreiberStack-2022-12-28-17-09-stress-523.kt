package mpp.stack

import kotlinx.atomicfu.atomic

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true) {
            val curent_top = top;
            val new_top = Node(x, curent_top.value)

            if (top.compareAndSet(curent_top.value, new_top)) {
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
            val curent_top = top;
            var new_top: Node<E>?

            if (curent_top.value != null) {
                new_top = curent_top.value!!.next!!
            } else {
                new_top = null
            }

            if (top.compareAndSet(curent_top.value, new_top)) {
                return new_top?.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT