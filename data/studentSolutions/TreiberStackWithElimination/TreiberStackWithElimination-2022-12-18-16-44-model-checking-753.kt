package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true) {
            val idx = ThreadLocalRandom.current().nextInt(0, ELIMINATION_ARRAY_SIZE)
            if (eliminationArray[idx].compareAndSet(null, x)) {
                for (tries in 1..3) {
                    if (eliminationArray[idx].value == true) {
                        return
                    }
                }
                // if not consumed in time
                if (!eliminationArray[idx].compareAndSet(x, null)) {
                    return
                }
                break
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
        for (idx in 0 until  ELIMINATION_ARRAY_SIZE) {
            val value = eliminationArray[idx].value
            if (value != null) {
                if (eliminationArray[idx].compareAndSet(value, null)) {
                    return value as? E?
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