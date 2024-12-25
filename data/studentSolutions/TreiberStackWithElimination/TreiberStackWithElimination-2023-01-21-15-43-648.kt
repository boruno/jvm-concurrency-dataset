//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)
    val random = Random(0)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val num = random.nextInt(ELIMINATION_ARRAY_SIZE - 1)
        for (i in num until num + 1) {
            if (eliminationArray[i].compareAndSet(null, x)) {
                for (j in 0..30) {
                    val el = eliminationArray[i].value
                    if (x == null || el != x) return
                }
                if (!eliminationArray[i].compareAndSet(x, null)) {
                    return
                } else {
                    while (true) {
                        val cur_head = top.value
                        if (top.compareAndSet(cur_head, Node(x, cur_head))) break
                    }
                    return
                }
            }
        }
        while (true) {
            val cur_head = top.value
            if (top.compareAndSet(cur_head, Node(x, cur_head))) break
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val num = random.nextInt(ELIMINATION_ARRAY_SIZE - 1)
        for (i in num until num + 1) {
            val el = eliminationArray[i].value
            if (el != null && eliminationArray[i].compareAndSet(el, null)) {
                return el as E?
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
