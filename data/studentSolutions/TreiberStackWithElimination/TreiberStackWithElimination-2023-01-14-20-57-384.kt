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
        val i = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE);
        if (eliminationArray[i].compareAndSet(null, x)) {
            if (!eliminationArray[i].compareAndSet(x, null)) {
                return;
            }
        }

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
    fun pop(): E? {
        val i = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE);
        val getValue = eliminationArray[i].getAndSet(null)
        if (getValue != null) {
            return getValue;
        }

        while (true) {
            var newTop: Node<E>? = null
            val curTop = top.value
            if (curTop != null) {
                newTop = curTop.next
            }

            if (top.compareAndSet(curTop, newTop)) {
                if (curTop == null) {
                    return null
                }
                return curTop.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT