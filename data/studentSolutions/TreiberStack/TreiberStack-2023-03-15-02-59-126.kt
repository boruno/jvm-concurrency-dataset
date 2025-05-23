//package mpp.stack

import kotlinx.atomicfu.atomic
import java.util.concurrent.atomic.AtomicReference

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true) {
            val curTop = top
            val newTop = AtomicReference(Node(x, curTop.value))
            if (top.compareAndSet(curTop.value, newTop.get())) {
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
            var curTop = top
            if (curTop.value == null) throw RuntimeException()
            var newTop = curTop.value!!.next
            if (top.compareAndSet(curTop.value, newTop)) {
                return curTop.value!!.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT