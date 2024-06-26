package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val idx = ThreadLocalRandom.current().nextInt() % ELIMINATION_ARRAY_SIZE
        if (eliminationArray[idx].compareAndSet(null, x)) {
            for (tries in 1..100) {
                if (eliminationArray[idx].value == null) {
                    return
                }
            }
            if (!eliminationArray[idx].compareAndSet(x, null)) {
                return
            }
        }

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
        for (i in 1.. ELIMINATION_ARRAY_SIZE/2) {
            val idx = ThreadLocalRandom.current().nextInt() % ELIMINATION_ARRAY_SIZE
            if (eliminationArray[idx].value != null) {
                val value = eliminationArray[idx].value
                if (eliminationArray[idx].compareAndSet(value, null)) {
                    return value
                }
            }
        }

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