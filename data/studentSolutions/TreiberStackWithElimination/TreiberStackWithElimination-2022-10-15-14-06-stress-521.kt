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
        for (i in 0 until eliminationArray.size) {
            val cell = eliminationArray[i]
            if (cell.compareAndSet(null, x)) {
                for (k in 1..ITERATIONS_TO_WAIT) {
                    if (cell.value != x) {
                        break
                    }
                }
                if (!cell.compareAndSet(x, null)) {
                    return
                }
                break
            }
        }
        honestPush(x)
    }

    private fun honestPush(x: E) {
        while (true) {
            val topCopy = top.value
            val newTop = Node(x, topCopy)
            if (top.compareAndSet(topCopy, newTop)) {
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
        for (i in 0 until eliminationArray.size) {
            val cell = eliminationArray[i]
            val value = cell.value
            if (value != null && cell.compareAndSet(value, null)) {
                return value
            }
        }
        return honestPop()
    }

    private fun honestPop(): E? {
        while (true) {
            val topCopy = top.value ?: return null
            if (top.compareAndSet(topCopy, topCopy.next)) {
                return topCopy.x
            }
        }
    }

    companion object {
        private const val ITERATIONS_TO_WAIT = 10
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT