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
        for (pos in 0 until ELIMINATION_ARRAY_SIZE) {
            if (eliminationArray[pos].compareAndSet(null, x)) {
                repeat(100) {
                    if (eliminationArray[pos].value != x) {
                        return
                    }
                }
                if (!eliminationArray[pos].compareAndSet(x, null)) {
                    return
                }
            }
        }
        pushStack(x)
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        for (pos in ELIMINATION_ARRAY_SIZE - 1 downTo 0) {
            val value = eliminationArray[pos].value ?: continue
            if (eliminationArray[pos].compareAndSet(value, null)) {
                return value
            }
        }
        return popStack()
    }

    /**
     * Adds the specified element [x] to the stack.
     */
    fun pushStack(x: E) {
        while (true) {
            val curTop = top.value
            val newTop = Node(x, curTop)
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
    fun popStack(): E? {
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