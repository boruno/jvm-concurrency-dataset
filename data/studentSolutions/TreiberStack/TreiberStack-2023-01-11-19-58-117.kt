//package mpp.stack

import kotlinx.atomicfu.atomic

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        var recent: Node<E>
        while (true) {
            recent = top.value!!
            var newEl = Node(x, recent)
            if (top.compareAndSet(recent, newEl)) {
                break
            }
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        var res: Node<E>
        var prev: Node<E>
        while (true) {
            res = top.value!!
            if (res == null) {
                return null;
            }
            prev = res.next!!
            if (top.compareAndSet(res, prev)) {
                return res.x
            }
        }
    }
}
private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT