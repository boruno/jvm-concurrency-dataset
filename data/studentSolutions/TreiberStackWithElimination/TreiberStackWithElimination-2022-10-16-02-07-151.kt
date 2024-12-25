//package mpp.stackWithElimination

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
        val index = Random.nextInt(0, ELIMINATION_ARRAY_SIZE)
        if (eliminationArray[index].compareAndSet(null, x)) {
            for (i in 0 until RETRY_COUNT) {
                if (eliminationArray[index].compareAndSet(null, null)) {
                    return
                }
            }
        }

        if (eliminationArray[index].compareAndSet(x, null)) {
            pushWithoutElimination(x)
        }
    }

    private fun pushWithoutElimination(x: E) {
        while (true) {
            val currentTop = top.value
            val newTop = Node(x, currentTop)
            if (top.compareAndSet(currentTop, newTop)) {
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
        val index = Random.nextInt(0, ELIMINATION_ARRAY_SIZE)
        val value = eliminationArray[index].getAndSet(null)
        if (value != null) {
            return value as E?
        }
        return popWithoutElimination()
    }


    private fun popWithoutElimination(): E? {
        while (true) {
            val currentTop = top.value ?: return null
            val newTop = currentTop.next
            if (top.compareAndSet(currentTop, newTop)) {
                return currentTop.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT

private const val RETRY_COUNT = 10
