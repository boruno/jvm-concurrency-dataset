package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)
    private val done: String = "DONE"

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        if (!putAndWait(x)) {
            while (true) {
                val curTop: Node<E>? = top.value
                val newTop: Node<E> = Node(x, curTop)

                if (top.compareAndSet(curTop, newTop)) {
                    return
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
        for (i in 0 until ELIMINATION_ARRAY_SIZE) {
            val cur = eliminationArray[i].value

            if (cur == null || cur == done) {
                continue
            }

            if (eliminationArray[i].compareAndSet(cur, done)) {
                return (cur as Node<E>).x
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

    private fun putAndWait(x: E): Boolean {
        var index = Integer.MAX_VALUE

        for (i in 0 until ELIMINATION_ARRAY_SIZE) {
            if (eliminationArray[i].compareAndSet(null, Node(x, null))) {
                index = i
                break
            }

        }

        if (index == Integer.MAX_VALUE) {
            return false
        }

        for (i in 0 until 10) {
            if (eliminationArray[index].value == done) {
                eliminationArray[index].value = null
                return true
            }
        }

        return if (!eliminationArray[index].compareAndSet(Node(x, null), null)) {
            eliminationArray[index].compareAndSet(done, null)
            true
        } else {
            false
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT