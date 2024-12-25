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
             val curr_head = top.value
             val new_head = Node(x, curr_head)
             if(CAS_check(curr_head,new_head)){
                 return
             }
         }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        while(true) {
            val curr_head = top.value
            if(curr_head === null)
                throw EmptyStackException()
            if(CAS_check(curr_head, curr_head.next)){
                    return curr_head.x
            }
        }
    }

    private fun CAS_check(old_top: Node<E>?, new_top: Node<E>?): Boolean{
        val old_curr = this.top
        if(old_curr.value === old_top) {
            top.value = new_top
            return true
       }
       return false
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT