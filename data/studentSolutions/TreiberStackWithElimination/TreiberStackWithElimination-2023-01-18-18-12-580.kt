//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val random: Random = Random(0)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        if (!pushWithElimination(x)) {
            while (true) {
                val curHead = top.value
                val newHead = Node(x, curHead)
                if (top.compareAndSet(curHead, newHead)) {
                    return
                }
            }
        }
    }

    private fun pushWithElimination(x: E): Boolean {
        val init = random.nextInt(ELIMINATION_ARRAY_SIZE - 2)
        for (i in init until init + 2) {
            if (eliminationArray[i].compareAndSet(null, x)) {
                for (j in 0..29) {
                    val value = eliminationArray[i].value
                    if (value == null || value != x) {
                        return true
                    }
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
        val init = random.nextInt(ELIMINATION_ARRAY_SIZE - 2)
        for (i in init until init + 2) {
            val value = eliminationArray[i].value
            if (value != null && eliminationArray[i].compareAndSet(value, null)) {
                return value as E?
            }
        }
        while (true) {
            val currHead = top.value ?: return null
            if (top.compareAndSet(currHead, currHead.next)) {
                return currHead.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
