//package mpp.stack

import kotlinx.atomicfu.atomic
import java.util.EmptyStackException

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
         while(true){
             val curr_head = top
             val new_head = Node(x, curr_head.value)
             if(curr_head.compareAndSet(curr_head.value, new_head))
                 return
         }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        while(true) {
            val curr_head = top
            if(curr_head.value == null)
                return null
            val curr_head_next = top.value!!.next
            if(curr_head.compareAndSet(curr_head.value, curr_head_next))
                return curr_head.value!!.x
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT