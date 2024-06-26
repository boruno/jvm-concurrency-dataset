package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val old1 = eliminationArray[0]
        if (old1.compareAndSet(null, x)) {
            for (i in 0..10) {
                if (old1.compareAndSet(null, null)) return
            }
            if (!old1.compareAndSet(x, null)) {
                return
            }
        } else {
            val old2 = eliminationArray[1]
            if (old2.compareAndSet(null, x)) {
                for (i in 0..10) {
                    if (old2.compareAndSet(null, null)) return
                }
                if (!old1.compareAndSet(x, null)) {
                    return
                }
            }
        }
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
        val old1 = eliminationArray[0]
        val value1 = old1.value
        if (value1 != null && old1.compareAndSet(value1, null)) {
            return value1
        }
        val old2 = eliminationArray[1]
        val value2 = old1.value
        if (value2 != null && old2.compareAndSet(value2, null)) {
            return value2
        }
        while (true) {
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