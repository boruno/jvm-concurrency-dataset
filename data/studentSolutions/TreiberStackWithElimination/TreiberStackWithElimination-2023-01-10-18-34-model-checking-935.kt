package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val idx = (0 until ELIMINATION_ARRAY_SIZE).random()
        val item = eliminationArray[idx]
        if (item.compareAndSet(null, x)) {
            repeat(100) {
                if (item.compareAndSet("DONE", null)) {
                    return
                }
            }
            if (item.compareAndSet(x, null)) {
                while (true) {
                    val node = Node(x, top.value)
                    if (top.compareAndSet(node.next, node)) {
                        return
                    }
                }
            } else {
                if (!item.compareAndSet("DONE", null)) {
                    throw RuntimeException("uuuu")
                }
            }
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val idx = (0 until ELIMINATION_ARRAY_SIZE).random()
        val item = eliminationArray[idx]
        val value = item.value

        if (value == null || value == "DONE") {
            while (true) {
                val node = top.value ?: return null
                if (top.compareAndSet(node, node.next)) {
                    return node.x;
                }
            }
        } else {
            if (item.compareAndSet(value, "DONE")) {
                return value as? E;
            } else {
                while (true) {
                    val node = top.value ?: return null
                    if (top.compareAndSet(node, node.next)) {
                        return node.x;
                    }
                }
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT