//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val pushingNode = Node(x, null)
        for (i in 0 .. 1) {
            if (!eliminationArray[i].compareAndSet(null, pushingNode)) continue
            for (j in 1 .. 10) {
                var curNode = eliminationArray[i].value
                if (curNode == DONE) {
                    eliminationArray[i].value = null; return
                }
            }

            if (eliminationArray[i].compareAndSet(pushingNode, null)) {
                while (true) {
                    val now = top.value
                    val future = Node(x, now)
                    if (top.compareAndSet(now, future)) return
                }
            } else {
                eliminationArray[i].value = null; return
            }

        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val future = DONE
        for (i in 0 .. 1) {
            val now = eliminationArray[i].value
            if (now != null && now != DONE && eliminationArray[i].compareAndSet(now, future)) {
                if (now is Node<*>) return (now as Node<E>).x
            }
        }

        while (true) {
            val now = top.value
            if (now == null) return now
            else if (top.compareAndSet(now, now.next)) return now.x
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT

private enum class Done {DONE}

private val DONE = Done.DONE