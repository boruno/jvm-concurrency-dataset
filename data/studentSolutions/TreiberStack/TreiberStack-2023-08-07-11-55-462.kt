//package day1

import java.util.concurrent.atomic.AtomicReference

class TreiberStack<E> : Stack<E> {
    // Initially, the stack is empty.
    private val top = AtomicReference<Node<E>?>(null)

    override fun push(element: E) {
        // TODO: Make me linearizable!
        // TODO: Update `top` via Compare-and-Set,
        // TODO: restarting the operation on CAS failure.
        top.getAndUpdate { top ->
            if (top == null) return@getAndUpdate null
            Node(element, top)
        }
    }

    override fun pop(): E? {
        // TODO: Make me linearizable!
        // TODO: Update `top` via Compare-and-Set,
        // TODO: restarting the operation on CAS failure.
        return top.getAndUpdate { top ->
                if (top == null) return@getAndUpdate null
                top.next
            }?.element
    }

    private class Node<E>(
        val element: E,
        val next: Node<E>?
    )
}