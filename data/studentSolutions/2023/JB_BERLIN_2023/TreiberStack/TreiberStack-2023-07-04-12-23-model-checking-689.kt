package day1

import kotlinx.atomicfu.*

class TreiberStack<E> : Stack<E> {
    // Initially, the stack is empty.
    private val top = atomic<Node<E>?>(null)

    override fun push(element: E) {
        while (true) {
            val curTop = top.value
            val newTop = Node(element, curTop)
            if(top.compareAndSet(curTop, newTop)){
                break
            }
        }
    }

    override fun pop(): E? {
        while (true) {
            val curTop = top.value ?: return null
            if (top.compareAndSet(curTop, curTop.next)){
                return top.value?.element
            }
        }
    }

    private class Node<E>(
        val element: E,
        val next: Node<E>?
    )
}