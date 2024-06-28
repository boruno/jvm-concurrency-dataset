package day1

import kotlinx.atomicfu.*

class TreiberStack<E> : Stack<E> {
    // Initially, the stack is empty.
    private val top = atomic<Node<E>?>(null)

    // true -- we can make the operation
    private fun <E> CAS(current: E, next: E): Boolean {
        return current != next
    }

    override fun push(element: E) {
        // TODO: Make me linearizable!
        // TODO: Update `top` via Compare-and-Set,
        // TODO: restarting the operation on CAS failure.

        while (true) {
            val curTop = top.value
            val newTop = Node(element, curTop)
            if (CAS(top.value, element)) {
                top.value = newTop
                return
            }

        }
    }

    override fun pop(): E? {
        // TODO: Make me linearizable!
        // TODO: Update `top` via Compare-and-Set,
        // TODO: restarting the operation on CAS failure.
        while (true) {
            val curTop = top.value ?: return null

            if (CAS(top.value, curTop)) {
                top.value = curTop.next.value
                return curTop.element
            }
        }
    }

    private class Node<E>(
        val element: E,
        next: Node<E>?
    ) {
        val next = atomic(next)
    }
}