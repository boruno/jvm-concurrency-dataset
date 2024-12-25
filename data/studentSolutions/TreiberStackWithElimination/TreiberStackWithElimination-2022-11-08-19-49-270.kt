//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

@Suppress("UNCHECKED_CAST")
class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any>(ELIMINATION_ARRAY_SIZE)
    private val GETTED = object {}

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val index = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        val old = eliminationArray[index]
        if (old.compareAndSet(null, x)) {
            for (i in 0..10) {
                if (old.compareAndSet(GETTED, null)) return
            }
            if (!old.compareAndSet(x, null)) {
                return
            }
        }
        while (true) {
            val curTop = top.value
            val newTop = Node(x, curTop)
            if (top.compareAndSet(curTop, newTop)) return
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val index = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        val old = eliminationArray[index]
        val value1 = old.value
        if (value1 != null && old.compareAndSet(value1, GETTED)) {
            return value1 as E?
        }
        while (true) {
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