//package mpp.stack

import kotlinx.atomicfu.atomic
import java.util.*

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true) {
            val cur_top = top
            val new_top = Node(x, cur_top.value)
            if (top.compareAndSet(cur_top.value, new_top)) {
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
            val cur_top = top
            if (cur_top.value == null) {
                throw EmptyStackException()
            }
            val new_top = cur_top.value?.next
            if (top.compareAndSet(cur_top.value, new_top)) {
                return cur_top.value?.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT