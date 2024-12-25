//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        // for (i in 0 until ELIMINATION_ARRAY_SIZE) {
            val i = 0//(0 until ELIMINATION_ARRAY_SIZE).random()
            if (eliminationArray[i].compareAndSet(null, x)) {
                for (j in 1..ELIMINATION_TIME) {
                    // if (eliminationArray[i].compareAndSet(null, null)) {
                    //     return
                    // }
                    if (eliminationArray[i].value != x) {
                        return
                    }
                }

                if (eliminationArray[i].compareAndSet(x, null)){
                    
                } else {
                    return
                }
            }

            while (true) {
                val node = top.value
                if (top.compareAndSet(node, Node<E>(x, node)))
                    return
            }
        // }
        
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        // for (i in 0 until ELIMINATION_ARRAY_SIZE) {
            val i = 0//(0 until ELIMINATION_ARRAY_SIZE).random()
            val value = eliminationArray[i].getAndSet(null)
            if (value != null) {
                return value
            }
            // val value = eliminationArray[i].value
            // if (value != null && eliminationArray[i].compareAndSet(value, null)) {
            //     return value
            // }
        // }
        while (true) {
            val node = top.value
            if (node == null) {
                return null
            } else if (top.compareAndSet(node, node.next)) {
                return node.x
            }
        }   
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
private const val ELIMINATION_TIME = 8
