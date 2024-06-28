package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*

class TreiberStackWithElimination<E> {
    val random = Random(0)
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        if (!process(x)) {
            while (true) {
                val cur_head = top.value
                if (top.compareAndSet(cur_head, Node(x, cur_head))) break
            }
        }
    }

    private fun process(x: E): Boolean {
        val num = random.nextInt(ELIMINATION_ARRAY_SIZE - 1)
        for (i in num until num + 1) {
            if (eliminationArray[i].compareAndSet(null, x)) {
                for (j in 0..10) {
                    val value = eliminationArray[i].value
                    if (value == null || value != x) return true
                }
                return !eliminationArray[i].compareAndSet(x, null)
            }
        }
        return false
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val num = random.nextInt(ELIMINATION_ARRAY_SIZE - 1)
        for (i in num until num + 1) {
            val value = eliminationArray[i].value
            if (value != null && eliminationArray[i].compareAndSet(value, null)) {
                return value as E?
            }
        }
        while (true) {
            val cur_head = top.value ?: return null
            if (top.compareAndSet(cur_head, cur_head.next)) {
                return cur_head.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
