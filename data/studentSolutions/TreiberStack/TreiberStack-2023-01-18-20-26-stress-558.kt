package mpp.stack

import kotlinx.atomicfu.atomic

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        var newHead: Node<E>?
        var cur: Node<E>?
        while (true) {
            cur = top.value
            newHead = Node<E>(x, cur)
            if(top.compareAndSet(cur, newHead)) break
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        var newHead: Node<E>?
        var cur: Node<E>?
        while (true) {
            cur = top.value
            if (cur == null) return null
            newHead = cur.next
            if (cur == top.value) return newHead?.x
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT