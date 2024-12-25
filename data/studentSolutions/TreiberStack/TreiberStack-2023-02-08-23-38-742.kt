//package mpp.stack

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    private fun CAS(cur_top: Node<E>?, new_top: Node<E>?): Boolean {
        if (!top.compareAndSet(cur_top, new_top))
            return false
        return true
    }

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true) {
            val curTop = top.value
            val newTop = Node(x, curTop?.next)
            if (CAS(curTop, newTop)) {
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
            val curTop = top.value
            if (curTop != null) {
                if (CAS(curTop, curTop.next)) {
                    return curTop.x
                }
            }
            return null
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT