//package mpp.stack

import kotlinx.atomicfu.atomic

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
		while (true) {
			val T = top.value
			val newHead = Node(x, T)
			if (top.compareAndSet(T, newHead)) {
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
			val T = top.value
			if (T == null) return null
			val next = top.value?.next
			if (top.compareAndSet(T, next)) {
				return top.value?.x
			}
		}
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT

