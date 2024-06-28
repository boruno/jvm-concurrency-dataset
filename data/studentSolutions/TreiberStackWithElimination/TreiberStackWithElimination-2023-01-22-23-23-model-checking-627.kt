package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random


class TreiberStackWithElimination<E> {
    private val head = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    private val ELIMINATION_SIZE = 15
    private val SPIN_WAIT = 50
    private val SEARCHING_RANGE = 5

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val rand: Int = Random.Default.nextInt(ELIMINATION_SIZE)
        for (i in Math.max(0, rand - SEARCHING_RANGE) until Math.min(ELIMINATION_SIZE, rand + SEARCHING_RANGE)) {
            val el = eliminationArray[i]
            if (el.compareAndSet(null, x)) {
                for (k in 0 until SPIN_WAIT) {
                    if (el.value == null) return
                }
                if (!el.compareAndSet(x, null)) return
                break
            }
        }
        while (true) {
            val curHead = head.value
            if (head.compareAndSet(curHead, Node(x, curHead))) return
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val rand = Random.Default.nextInt(ELIMINATION_SIZE)
        for (i in Math.max(0, rand - SEARCHING_RANGE) until Math.min(ELIMINATION_SIZE, rand + SEARCHING_RANGE)) {
            val el = eliminationArray[i]
            val value = el.value
            if (value != null) {
                if (el.compareAndSet(value, null)) {
                    return value as E
                }
            }
        }
        while (true) {
            val curHead = head.value ?: return null
            if (head.compareAndSet(curHead, curHead.next.value)) return curHead.x
        }
    }
}

private class Node<E>(val x: E, next: Node<E>?) {
    val next = atomic(next)
}

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT