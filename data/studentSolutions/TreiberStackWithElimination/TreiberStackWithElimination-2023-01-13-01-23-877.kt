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
        val random_cell = (0..ELIMINATION_ARRAY_SIZE).random()
        if (eliminationArray[random_cell].compareAndSet(null, x))
        {
            for (j in 0 until TIMEOUT) {
                val cur = eliminationArray[random_cell].value
                if (cur == null || cur != x) {
                    return
                }
            }

            while (true) {
                if (eliminationArray[random_cell].compareAndSet(x, null))
                {
                    break
                }
            }
        }

        while (true) {
            val cur_top = top.value
            val new_top = Node(x, cur_top)
            if (top.compareAndSet(cur_top, new_top)) {
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
        val random_cell = (0..ELIMINATION_ARRAY_SIZE).random()
        val item = eliminationArray[random_cell].value
        if (item != null) {
            if (eliminationArray[random_cell].compareAndSet(item, null)) {
                return item
            }
        }

        while (true) {
            val cur_top = top.value
            if (cur_top == null) {
                return null
            }
            val new_top = cur_top.next
            if (top.compareAndSet(cur_top, new_top)) {
                return cur_top.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
private const val TIMEOUT = 10