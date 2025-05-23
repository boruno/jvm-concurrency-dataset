//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Node<E>>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        if (putInArrayAndWait(x)) {
            while (true) {
                val curTop: Node<E>? = top.value
                val newTop: Node<E> = Node(x, curTop)
                if (top.compareAndSet(curTop, newTop)) {
                    return
                }
            }
        }
    }

    private fun putInArrayAndWait(x: E): Boolean {
        val nodeToPut: Node<E> = Node(x, null);
        var index: Int = -1
        var waitingTime: Int = 10
//        while (true) {
        for (i in 0..ELIMINATION_ARRAY_SIZE - 1) {
            if (eliminationArray[index].compareAndSet(null, nodeToPut)) {
                index = i
            }
//                index = (index + 1) % ELIMINATION_ARRAY_SIZE
        }
//        }
        if (index == -1) {
            return true
        }
        while(waitingTime != 0) {
            if (eliminationArray[index].value != nodeToPut) {
                return false
            }
            waitingTime -= 1
        }
        return eliminationArray[index].compareAndSet(nodeToPut, null)
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        for (i in 0..ELIMINATION_ARRAY_SIZE - 1) {
            val cur = eliminationArray[i].value
            if (cur != null && eliminationArray[i].compareAndSet(cur, null)) {
                return cur.x
            }
        }

        while (true) {
            val curTop: Node<E> = top.value ?: return null
            val newTop: Node<E>? = curTop.next
            if (top.compareAndSet(curTop, newTop)) {
                return curTop.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT