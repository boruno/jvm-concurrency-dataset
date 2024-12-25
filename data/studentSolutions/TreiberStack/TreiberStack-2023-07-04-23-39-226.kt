//package day1

import kotlinx.atomicfu.*
import java.util.EmptyStackException

class TreiberStack<E> : Stack<E> {
    // Initially, the stack is empty.
    private val top = atomic<Node<E>?>(null)

    override fun push(element: E) {
        while (true) {
            val curTop = top.value
            val newTop = Node(element, curTop)
            if (top.compareAndSet(curTop, newTop))
                return;
        }
    }

    override fun pop(): E? {
        while (true) {
            val curTop = top.value ?: throw EmptyStackException()
            val newTop = curTop.next.value
            if (top.compareAndSet(curTop, newTop))
                return curTop.element;

        }
    }

    private class Node<E>(
        val element: E,
        next: Node<E>?
    ) {
        val next = atomic(next)
    }
}