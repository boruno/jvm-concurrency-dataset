package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        for (i in 0 until ELIMINATION_ARRAY_SIZE) {
            if (eliminationArray[i].compareAndSet(null, x)) {
                for (j in 0..100) {
                    if (eliminationArray[i].value != x) {
                        return
                    }
                }
                if (eliminationArray[i].compareAndSet(x, null)) {
                    break
                }
                return
            }
        }

        while(true) {
            val curTop = top.value
            val newTop = Node<E>(x, curTop)
            if (top.compareAndSet(curTop, newTop)) {
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
        for (i in 0 until ELIMINATION_ARRAY_SIZE) {
            val element = eliminationArray[i].value
            if (element != null) {
                if (eliminationArray[i].compareAndSet(element, null)) {
                    return element as E?
                }
            }
        }
        while(true) {
            val curTop = top.value ?: return null
            val newTop = curTop.next
            if (top.compareAndSet(curTop, newTop)) {
                return curTop.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT