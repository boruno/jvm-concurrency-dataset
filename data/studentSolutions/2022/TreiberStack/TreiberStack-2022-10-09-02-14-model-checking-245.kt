package mpp.stack

import kotlinx.atomicfu.atomic

/**
 * @author Viktor Panasyuk
 */
class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        TODO("implement me")
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        while (true) {
            val head = top.value
            if (head != null) {
                if (top.compareAndSet(head, head.next)) {
                    return head.value;
                }
            }
        }
    }
}

private class Node<E>(val value: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT