//package mpp.stack

import kotlinx.atomicfu.atomic
import java.util.EmptyStackException

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true) {
            val curTop = top.value
            val newTop = Node(x, curTop)
            if (top.compareAndSet(curTop, newTop)) return
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
       while (true) {
           val curTop: Node<E>? = top.value ?: throw EmptyStackException()
           val newTop = curTop?.next
           if( top.compareAndSet(curTop, newTop)) {
               if (curTop != null) {
                   return  curTop.x
               }
               return null
           }
       }
    }
}

private class Node<E>(val x: E, var next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT