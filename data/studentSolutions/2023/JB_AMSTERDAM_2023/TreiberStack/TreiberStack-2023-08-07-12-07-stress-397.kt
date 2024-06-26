package day1

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
            if (cas(top, curTop, newTop)) {
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
            if (cas(top, curTop, curTop.next)) {
                return curTop.element
            }
        }
    }

    private fun cas(obj: AtomicReference<Node<E>?>, prev: Node<E>?, new: Node<E>?): Boolean {
        if (obj.get() != prev) {
            return false
        }
        obj.set(new)
        return true
    }

    private class Node<E>(
        val element: E,
        val next: Node<E>?
    )
}