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
        while (true) {
            val curTop = top.value
            val newTop = Node(x, curTop)
            var elimIndex = -1
            if (!eliminationArray[0].compareAndSet(null, newTop.x)) {
                if (!eliminationArray[1].compareAndSet(null, newTop.x)) {
                    if (top.compareAndSet(curTop, newTop)) {
                        return
                    }
                } else {
                    elimIndex = 1
                }
            } else {
                elimIndex = 0
            }
            var i = 0
            while (i != 100 && eliminationArray[elimIndex].compareAndSet(null, null)) {
                i += 1
            }
            return

        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        while (true) {
            if (!eliminationArray[1].compareAndSet(null, null)) {
                return eliminationArray[1].value as E
            } else {
                if (!eliminationArray[0].compareAndSet(null, null)) {
                    return eliminationArray[0].value as E
                }
            }
            val curTop = top.value ?: return null
            val newTop = curTop.next
            if (top.compareAndSet(curTop, newTop)) {
                return curTop.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT