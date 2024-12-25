//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true){
            val oldTop = top.value
            if (top.compareAndSet(oldTop, Node(x,oldTop))){
                return
            }else{
                if(eliminationArray[0].compareAndSet(null,x)){
                    for (i in 0..5){
                        if(eliminationArray[0].value==null){
                            return
                        }
                    }
                }
            }
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        var oldTop = top.value
        do {

            if(eliminationArray[0].value!=null){
                val value = eliminationArray[0].value
                if(eliminationArray[0].compareAndSet(value,null)){
                    return value
                }
            }
            oldTop = top.value
            oldTop ?: return null
        } while (!top.compareAndSet(oldTop, oldTop!!.next))
        return oldTop.x
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT