//package mpp.stack

import kotlinx.atomicfu.atomic

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)
    fun push(x: E)
    {
        while (true) {
            val newTop = Node<E>(x, top.value)
            if (top.compareAndSet(top.value, newTop)) return
        }

    }
    fun pop() : E?
    {
        while (true) {
            val curTop = top.value ?: return null
            val nextTop = curTop.next
            if (top.compareAndSet(curTop, nextTop)) {
                return curTop.x
            }
        }
    }

}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT