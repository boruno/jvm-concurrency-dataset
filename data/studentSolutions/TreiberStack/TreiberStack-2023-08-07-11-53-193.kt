//package day1

import java.util.concurrent.atomic.AtomicReference

class TreiberStack<E> : Stack<E> {
    // Initially, the stack is empty.
    private val top = AtomicReference<Node<E>?>(null)

    override fun push(element: E) {
        // TODO: Make me linearizable!
        // TODO: Update `top` via Compare-and-Set,
        // TODO: restarting the operation on CAS failure.
        val curTop = top.get()
        val newTop = Node(element, curTop)
        if (top != newTop)
            top.set(newTop)
    }

    override fun pop(): E? {
        // TODO: Make me linearizable!
        // TODO: Update `top` via Compare-and-Set,
        // TODO: restarting the operation on CAS failure.
        val curTop = top.get() ?: return null
        if (curTop.next != top)
            top.set(curTop.next)
            return curTop.element
    }

    private class Node<E>(
        val element: E,
        val next: Node<E>?
    ) {
        override fun equals(other: Any?): Boolean =
            element == (other as Node<E>).element && next == other.next
    }
}