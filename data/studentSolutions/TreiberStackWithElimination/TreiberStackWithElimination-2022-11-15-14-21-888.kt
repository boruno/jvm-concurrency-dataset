package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.stream.IntStream.range

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        for (i in range(0, ELIMINATION_ARRAY_SIZE)) {
            if (eliminationArray[i].compareAndSet(null, x)) {
                for (j in range(0, 25)) {
                    if (eliminationArray[i].value == null) {
                        return
                    }
                }
                break
            } else {
                continue
            }
        }
        while (true) {
            val newTop: Node<E> = Node(x, null)
            val oldTop: Node<E>? = top.value
            newTop.next = oldTop
            if (top.compareAndSet(oldTop, newTop)) {
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
        if (top.value == null) {
            for (i in ELIMINATION_ARRAY_SIZE - 1 downTo 0 step 1) {
                val value = eliminationArray[i].value
                if (value != null) {
                    if (eliminationArray[i].compareAndSet(value, null)) {
                        return value as E?
                    } else {
                        continue
                    }
                }
            }
        }
        while (true) {
            var newTop: Node<E>?
            val oldTop: Node<E> = top.value ?: return null
            newTop = oldTop.next
            if (top.compareAndSet(oldTop, newTop)) {
                return oldTop.x
            }
        }
    }
}

private class Node<E>(val x: E, var next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT