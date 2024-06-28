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
        for (i in 0..ELIMINATION_ARRAY_SIZE - 1) {
            if (eliminationArray[i].compareAndSet(null, x)) {
                for (c in 0..10) {
                    if (eliminationArray[i].value == null) {
                        return
                    }
                }
                if ( ! eliminationArray[i].compareAndSet(x, null)) {
                    return
                }
                break
            }
        }

        while (true) {
            val head = top.value
            if (top.compareAndSet(head, Node(x, head))) {
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
        for (i in 0..ELIMINATION_ARRAY_SIZE - 1) {
            val x: E? = eliminationArray[i].value
            if (x != null) {
                if (eliminationArray[i].compareAndSet(x, null)) {
                    return x
                }
            }
        }

        while (true) {
            val head = top.value ?: return null
            if (top.compareAndSet(head, head.next)) {
                return head.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT