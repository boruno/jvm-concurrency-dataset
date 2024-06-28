package mpp.stack

import kotlinx.atomicfu.atomic
import java.util.EmptyStackException

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true) {
            val cur_top = top.value
            var new_top = if (cur_top != null) {
                Node(cur_top.x, Node(x, null))
            } else {
                Node(x, null)
            }
            if (cas(cur_top, new_top)) return
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        while (true) {
            val cur_top = top.value
            if (cur_top == null) {
                throw EmptyStackException()
            }
            val new_top = cur_top.next
            if (cas(cur_top, new_top)) {
                return cur_top.x
            }
        }
    }

    @Synchronized
    private fun cas(cur: Node<E>?, new: Node<E>?): Boolean {
        if (top.value != cur) {
            return false
        }
        top.value = new
        return true
    }

}


private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT