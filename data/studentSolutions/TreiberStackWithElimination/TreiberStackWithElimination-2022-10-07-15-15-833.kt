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
        var index = -1;
        if (eliminationArray[0].compareAndSet(null, x)) {
            index = 0;
        }
        if (index == -1 && eliminationArray[1].compareAndSet(null, x)) {
            index = 1;
        }
        if (index != -1 && !eliminationArray[index].compareAndSet(x, null)) {
            return
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
        var x: E?
        x = eliminationArray[0].getAndSet(null)
        if (x == null) {
            x = eliminationArray[1].getAndSet(null)
        }
        if (x == null) {
            while (true) {
                val curTop = top.value ?: return null
                val newTop = curTop.next
                if (top.compareAndSet(curTop, newTop)) {
                    x = curTop.x
                    break
                }
            }
        }
        return x
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT