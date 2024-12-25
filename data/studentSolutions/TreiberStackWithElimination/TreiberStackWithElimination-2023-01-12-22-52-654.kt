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
        for (i in 0 .. eliminationArray.size) {
            val item = x
            if (eliminationArray.get(i).compareAndSet(null, item)) {
                for (j in 0 .. TIMEOUT) {
                    val cur = eliminationArray.get(i).value
                    if (cur == null || cur != x) {
                        return
                    }
                }
                if (eliminationArray.get(i).compareAndSet(item, null)) {
                    break;
                }
                return
            }
        }

        //do this if elimination array wasn't useful
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
        for (i in 0 .. eliminationArray.size) {
            val item = eliminationArray.get(i).value
            if (item != null && eliminationArray.get(i).compareAndSet(item, null)) {
                return item
            }
        }

        //do this if elimination array wasn't useful
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
private const val TIMEOUT = 200 // DO NOT CHANGE IT