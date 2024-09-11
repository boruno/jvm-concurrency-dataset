package mpp.stack

import kotlinx.atomicfu.atomic
import kotlin.math.exp

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true) {
            val expected = top.value
            val newHead = Node(x, expected)
            if (top.compareAndSet(expected, newHead)) {
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
            val expected = top.value
            val new = top.value?.next
            if (top.compareAndSet(expected, new)) {
                return expected?.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT