//package day1

import java.util.concurrent.atomic.AtomicReference

class TreiberStack<E> : Stack<E> {
    // Initially, the stack is empty.
    private val top = AtomicReference<Node<E>?>(null)

    override fun push(element: E) {
        // TODO: Make me linearizable!
        // TODO: Update `top` via Compare-and-Set,
        // TODO: restarting the operation on CAS failure.
        while (true) {
            val curTop = top.get()
            val newTop = Node(element, curTop)
            if (cas(curTop, newTop)) {
                return
            }
        }
    }

    override fun pop(): E? {
        // TODO: Make me linearizable!
        // TODO: Update `top` via Compare-and-Set,
        // TODO: restarting the operation on CAS failure.
        while (true) {
            val curTop = top.get() ?: return null
            val newTop  = curTop.next
            if (cas(curTop, newTop)) {
                return curTop.element
            }
        }
    }

    private fun cas(
        curTop: Node<E>?,
        newTop: Node<E>?
    ): Boolean {
        val localTop = top.get()
        return if (localTop == curTop) {
            top.set(newTop)
            true
        } else {
            false
        }
    }

    private class Node<E>(
        val element: E,
        val next: Node<E>?
    )
}