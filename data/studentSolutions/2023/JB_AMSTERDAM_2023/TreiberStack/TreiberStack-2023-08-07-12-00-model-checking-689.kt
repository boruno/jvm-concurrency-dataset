package day1

import java.util.concurrent.atomic.AtomicReference

class TreiberStack<E> : Stack<E> {
    // Initially, the stack is empty.
    private val top = AtomicReference<Node<E>?>(null)

    override fun push(element: E) {
        // TODO: Make me linearizable!
        // TODO: Update `top` via Compare-and-Set,
        // TODO: restarting the operation on CAS failure.
        var curTop = top.get()
        while (!top.compareAndSet(curTop, Node(element, curTop)))
            curTop = top.get()
    }

    override fun pop(): E? {
        // TODO: Make me linearizable!
        // TODO: Update `top` via Compare-and-Set,
        // TODO: restarting the operation on CAS failure.
        var curTop = top.get() ?: return null
        while (!top.compareAndSet(curTop, curTop.next))
            curTop = top.get()?.next ?: return null

        return curTop.element


//        top.set(curTop.next)
//        return curTop.element
    }

    private class Node<E>(
        val element: E,
        val next: Node<E>?
    )
}