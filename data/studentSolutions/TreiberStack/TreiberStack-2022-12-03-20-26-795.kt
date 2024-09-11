package mpp.stack

import kotlinx.atomicfu.atomic
import java.util.*

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true) {
            if (top.value == null) {
                throw EmptyStackException();
            }
            val newHead = Node(x, top.value)
            if (cas(top.value!!.x, newHead.x)) {
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
            if (cas(top.value!!.x, top.value!!.next!!.x)) {
                return top.value?.x
            }
        }
    }

    private fun cas(old : E, new : E) : Boolean {
        if (top.value?.x != old) {
            return false
        }
        val n = Node(new, top.value)
        top.lazySet(n)
        return true
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT