package day1

import kotlinx.atomicfu.*
import kotlinx.atomicfu.locks.withLock
import java.util.concurrent.locks.ReentrantLock

class TreiberStack<E> : Stack<E> {
    // Initially, the stack is empty.
    private val top = atomic<Node<E>?>(null)
    private val lock = ReentrantLock()

    override fun push(element: E) = lock.withLock {
        // TODO: Make me linearizable!
        // TODO: Update `top` via Compare-and-Set,
        // TODO: restarting the operation on CAS failure.
        while (true) {
            val curTop = top.value
            val newTop = Node(element, curTop)
            if (top.compareAndSet(curTop, newTop)) {
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
            if (top.compareAndSet(curTop, curTop.next)) {
                return curTop.element
            }
        }
    }

    private class Node<E>(
        val element: E,
        val next: Node<E>?
    )
}