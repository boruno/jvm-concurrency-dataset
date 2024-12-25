//package mpp.stack

import kotlinx.atomicfu.atomic

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true) {
            val prev = top.value
            val cur = Node(x, prev)
            if (check(cur, prev)) {
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
            val prev = top.value
            if (check(prev?.next, top.value)) {
                return top.value?.x
            }
        }
    }

    private fun check(cur: Node<E>?, prev: Node<E>?): Boolean {
        return top.compareAndSet(prev, cur)
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT