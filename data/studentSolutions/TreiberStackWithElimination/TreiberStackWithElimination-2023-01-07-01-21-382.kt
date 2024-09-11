package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        var flag = false
        val index = Random.nextInt(2)
        if(eliminationArray[index].value == null){
            eliminationArray[index].lazySet(x)
            flag = true
            return
        }

        while(true){
            val curr_head = top.value
            val new_head = Node(x, curr_head)
            if(top.compareAndSet(curr_head, new_head))
                return
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val index = Random.nextInt(1)
        if(eliminationArray[index].value != null){
            val res = eliminationArray[index].value
            eliminationArray[index].lazySet(null)
            return res as E
        }

        while(true) {
            val curr_head = top.value
            if(curr_head == null)
                return null
            val curr_head_next = curr_head.next
            if(top.compareAndSet(curr_head, curr_head_next))
                return curr_head.x
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT