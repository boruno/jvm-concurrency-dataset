//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<E>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        var arrayIndex = -1
        repeat(100) {
            val index = Random.nextInt(0, ELIMINATION_ARRAY_SIZE)
            if (eliminationArray[index].value == null && arrayIndex == -1) {
                eliminationArray[index].value = x
                arrayIndex = index
            }
            if (eliminationArray[index].value == null && arrayIndex != -1) {
                return
            }
        }
        if (arrayIndex == -1) {
            while (true) {
                val curTop = top.value
                val newTop = Node(x, curTop)
                if (top.compareAndSet(curTop, newTop)) {
                    return
                }
            }
        }
        return
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        repeat(100) {
            val index = Random.nextInt(0, ELIMINATION_ARRAY_SIZE)
            if (eliminationArray[index].value != null) {
                val value = eliminationArray[index]
                eliminationArray[index].value = null
                return value.value
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