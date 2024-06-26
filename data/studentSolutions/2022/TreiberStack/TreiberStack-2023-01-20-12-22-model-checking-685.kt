package mpp.stack

import kotlinx.atomicfu.atomic

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        var newHead = Node(x)
        var oldHead: Node<E>
        do {
            oldHead = top.value!!
            newHead.next = oldHead;
        } while (!top.compareAndSet(oldHead, newHead));
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        var oldHead: Node<E>
        var newHead: Node<E>
        do {
            oldHead = top.value!!
            if (oldHead == null)
                return null
            newHead = oldHead.next!!
        } while (!top.compareAndSet(oldHead, newHead))
        return oldHead.x
    }
}

private class Node<E>(val x: E, var next: Node<E>?) {
    constructor(x: E) : this(x, null)
}

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT