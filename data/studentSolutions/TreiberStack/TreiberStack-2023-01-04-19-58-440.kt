//package mpp.stack

import kotlinx.atomicfu.atomic

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    fun push(x: E)
    {
        while (true) {
            val curTop = top.value
            val newTop = Node<E>(x, curTop)
            if (top.compareAndSet(curTop, newTop))
                return
        }

    }

    fun pop() : E?
    {
        while (true) {
            val curTop = top.value
            if (curTop == null) {
                return null
            }
                val nextTop = curTop.next
                top.compareAndSet(curTop, nextTop);
                return curTop.x
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT