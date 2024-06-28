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
        while(true) {
            val currentTop = top.value
            val newTop = Node<E>(x, currentTop)
            for (i in 1..100 step 1) {
                if (!eliminationArray[newTop.hashCode()].compareAndSet(null, newTop)) { return }
            }
            if (top.compareAndSet(currentTop, newTop)) { return }
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        while(true) {
            val currentTop = top.value ?: return null
            val newTop = currentTop.next
            if(!eliminationArray[currentTop.hashCode()].compareAndSet(currentTop, null)) {
                top.value = newTop
                return currentTop.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT