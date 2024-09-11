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
        val randomIndex = Random.nextInt(ELIMINATION_ARRAY_SIZE - 1)
        val node = eliminationArray[randomIndex].value
        if (node != null) insert(x)
        if (!eliminationArray[randomIndex].compareAndSet(null, x)) insert(x)
        repeat(10000) {}
        if (eliminationArray[randomIndex].compareAndSet(x, null)) insert(x)
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val randomIndex = Random.nextInt(ELIMINATION_ARRAY_SIZE - 1)
        while (true) {
            val node = eliminationArray[randomIndex].value
            if (node == null) {
                while (true) {
                    val currentTop = top.value ?: return null
                    val newTop = currentTop.next
                    if (top.compareAndSet(currentTop, newTop)) {
                        return currentTop.x
                    }
                }
            }
            if (eliminationArray[randomIndex].compareAndSet(node, null)) {
                return (node as Node<*>).x as E?
            }
        }
    }

    private fun insert(x: E) {
        while (true) {
            val currentTop = top.value
            val newTop = Node(x, currentTop)
            if (top.compareAndSet(currentTop, newTop)) return
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT