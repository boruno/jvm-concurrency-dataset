//package day1

import kotlinx.atomicfu.*

class TreiberStack<E> : Stack<E> {
    // Initially, the stack is empty.
    private val top = atomic<Node<E>?>(null)

    override fun push(element: E) {
        // TODO: Make me linearizable!
        // TODO: Update `top` via Compare-and-Set,
        // TODO: restarting the operation on CAS failure.
        while (true) {
            val curTop = top.value ?: throw Exception()
            val newTop = Node(element, curTop)
//            top.value = newTop
            if (cas(top.value!!, curTop) { top.value = newTop }) return
        }
    }

    override fun pop(): E? {
        // TODO: Make me linearizable!
        // TODO: Update `top` via Compare-and-Set,
        // TODO: restarting the operation on CAS failure.
        val curTop = top.value ?: return null
        top.value = curTop.next.value
        return curTop.element
    }

    private fun cas(cur: Node<E>, expected: Node<E>, setNew: () -> Unit ): Boolean {
        if (cur != expected) return false
        setNew()
        return true
    }

    private class Node<E>(
        val element: E,
        next: Node<E>?
    ) {
        val next = atomic(next)
    }
}