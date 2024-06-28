package mpp.stack

import kotlinx.atomicfu.atomic

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        // top.update{t -> Node(x, t)}
        // top.value.next = Node<E>(x, top.value.next)
        // TODO("implement me")
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val v = top.value
        if (v != null) {
            val t = v.next
            if (t != null)
                return t.x
            else
                throw NullPointerException()

        } else {
            throw NullPointerException()
        }
        // val t = top.value.next
        // top.value.next = t.value.next
        // return t.x
        // TODO("implement me")
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT