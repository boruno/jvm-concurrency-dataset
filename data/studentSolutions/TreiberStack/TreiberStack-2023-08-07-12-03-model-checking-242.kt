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
            if (top == curTop) {
                top.set(newTop)
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
            val newTop = curTop.next
            if (curTop == top)
                top.set(newTop)
                return curTop.element
        }

    }

    private class Node<E>(
        val element: E,
        val next: Node<E>?
    ) {
//        override fun equals(other: Any?): Boolean {
//            if (other == null) return false
//            if (!(other is Node<*>)) return false
//            return element == other.element && next == other.next
//        }

    }
}