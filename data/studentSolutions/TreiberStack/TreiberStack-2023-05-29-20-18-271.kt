//package day1

import kotlinx.atomicfu.*
import java.util.EmptyStackException

class TreiberStack<E> : Stack<E> {
    // Initially, the stack is empty.
    private val top = atomic<Node<E>?>(null)

    override fun push(element: E) {
        // TODO: Make me linearizable!
        // TODO: Update `top` via Compare-and-Set,
        // TODO: restarting the operation on CAS failure.
        while (true) {
            val curTop = top.value
            if (top.compareAndSet(curTop, Node(element, curTop))) {
                return
            }
        }
//        val curTop = top.value
//        val newTop = Node(element, curTop)

    }

    override fun pop(): E? {
        // TODO: Make me linearizable!
        // TODO: Update `top` via Compare-and-Set,
        // TODO: restarting the operation on CAS failure.
//        val curTop = top.value ?: return null
//        top.value = curTop.next.value
//        return curTop.element
        while (true) {
            val curTop = top.value ?: throw EmptyStackException()
            val newTop = curTop.next.value
            if (top.compareAndSet(curTop, newTop)) {
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