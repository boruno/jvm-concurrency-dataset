package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val i = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        val arrElem = eliminationArray[i]
        arrElem.value?.let {
            pushUtil(x)
            return
        }
        if (arrElem.compareAndSet(null, x)) {
            for (wait in 0 until 500) {
            }
            if (arrElem.compareAndSet(x, null)) pushUtil(x)
        } else pushUtil(x)
    }

    private fun pushUtil(x: E) {
        do {
            val prev = top.value
            val head = Node(x, prev)
        } while (!top.compareAndSet(prev, head))
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val i = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        val arrElem = eliminationArray[i]
        while (true) {
            val elem = arrElem.value?.let {
                if (arrElem.compareAndSet(it, null)) return it
            }
            if (elem == null) {
                var prev: Node<E>?
                do {
                    prev = top.value
                    if (prev == null) return null
                    val head = prev.next
                } while (!top.compareAndSet(prev, head))
                return prev!!.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT