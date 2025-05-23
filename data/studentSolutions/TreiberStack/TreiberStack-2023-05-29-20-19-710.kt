//package day1

import kotlinx.atomicfu.*

class TreiberStack<E> : Stack<E> {
    // Initially, the stack is empty.
    private val top = atomic<Node<E>?>(null)

    override fun push(element: E) {
        do {
            val curTop = top.value
            val newTop = Node(element, curTop)
        } while (top.compareAndSet(curTop, newTop))
    }

    override fun pop(): E? {
        var curTop : Node<E>
        do {
            curTop = top.value ?: return null
            val newTop = curTop.next
        } while (top.compareAndSet(curTop, newTop.value))
        return curTop.element
    }

    private class Node<E>(val element: E, next: Node<E>?) {
        val next = atomic(next)
    }
}