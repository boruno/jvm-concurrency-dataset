package day1

import kotlinx.atomicfu.*

class TreiberStackWithElimination<E> : Stack<E> {
    // Initially, the stack is empty.
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    override fun push(element: E) {
        TODO("Not yet implemented")
    }

    override fun pop(): E? {
        TODO("Not yet implemented")
    }

    private class Node<E>(
        val element: E,
        next: Node<E>?
    ) {
        val next = atomic(next)
    }

    companion object {
        private const val ELIMINATION_ARRAY_SIZE = 2 // Do not change!
    }
}