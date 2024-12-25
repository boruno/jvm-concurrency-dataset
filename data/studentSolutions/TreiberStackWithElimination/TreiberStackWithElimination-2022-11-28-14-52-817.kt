//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)

    private val repeatIterations = 10 * ELIMINATION_ARRAY_SIZE

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val index = Random.nextInt(0, ELIMINATION_ARRAY_SIZE)
        if (eliminationArray[index].compareAndSet(null, x)) {
            repeat(repeatIterations) {
                if (eliminationArray[index].value == null) {
                    return
                }
            }
            if (!eliminationArray[index].compareAndSet(x, null)) {
                return
            }
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
        repeat(repeatIterations) {
            val index = Random.nextInt(0, ELIMINATION_ARRAY_SIZE)
            val value = eliminationArray[index].value
            if (value != null && eliminationArray[index].compareAndSet(value, null)) {
                return value
            }
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